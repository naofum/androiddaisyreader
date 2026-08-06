package org.androiddaisyreader.base;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.androiddaisyreader.apps.PrivateException;
import org.androiddaisyreader.controller.AudioPlayerController;
import org.androiddaisyreader.model.Audio;
import org.androiddaisyreader.model.CurrentInformation;
import org.androiddaisyreader.model.DaisyBook;
import org.androiddaisyreader.model.Navigator;
import org.androiddaisyreader.model.Part;
import org.androiddaisyreader.model.Section;
import org.androiddaisyreader.model.Snippet;
import org.androiddaisyreader.sqlite.SQLiteCurrentInformationHelper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import android.media.MediaPlayer;

/**
 * ReaderPresenter のユニットテスト。
 * View / BaseMode / SQLite をモック化し、ナビゲーション・再生制御ロジックを検証する。
 */
public class ReaderPresenterTest {

    @Mock private ReaderView mockView;
    @Mock private DaisyEbookReaderBaseMode mockBaseMode;
    @Mock private SQLiteCurrentInformationHelper mockSql;
    @Mock private DaisyBook mockBook;
    @Mock private Navigator mockNavigator;
    @Mock private MediaPlayer mockPlayer;
    @Mock private AudioPlayerController mockAudioPlayer;

    private ReaderPresenter presenter;
    private static final String TEST_PATH = "/data/cache/books/test.zip";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockView.getActivityName()).thenReturn("TestActivity");
        when(mockView.isActivityFinishing()).thenReturn(false);
    }

    // ========================================================================
    // openBook テスト
    // ========================================================================

    @Test
    public void openBook_daisy202_success() throws Exception {
        when(mockBaseMode.openBook202()).thenReturn(mockBook);
        when(mockBook.hasTotalTime()).thenReturn(true);

        presenter = spy(new ReaderPresenter(mockView, mockBaseMode, mockSql, TEST_PATH, true));
        doNothing().when(presenter).initAudioPlayer(anyString());
        doReturn(mockNavigator).when(presenter).createNavigator(any(DaisyBook.class));

        presenter.openBook();

        assertNotNull(presenter.getBook());
        assertEquals(mockBook, presenter.getBook());
        assertNotNull(presenter.getNavigator());
    }

    @Test
    public void openBook_daisy202_failure_showsError() throws Exception {
        when(mockBaseMode.openBook202()).thenThrow(new PrivateException("test error"));

        presenter = spy(new ReaderPresenter(mockView, mockBaseMode, mockSql, TEST_PATH, true));
        doNothing().when(presenter).initAudioPlayer(anyString());
        doReturn(mockNavigator).when(presenter).createNavigator(any(DaisyBook.class));

        presenter.openBook();

        assertNull(presenter.getBook());
        verify(mockView).showErrorDialog(any(Exception.class));
    }

    @Test
    public void openBook_daisy30_success() throws Exception {
        when(mockBaseMode.openBook30()).thenReturn(mockBook);
        when(mockBaseMode.getPathExactlyDaisy30(TEST_PATH)).thenReturn(TEST_PATH);

        // Navigator for listId extraction
        Navigator tempNavigator = mock(Navigator.class);
        when(tempNavigator.hasNext()).thenReturn(false);

        presenter = spy(new ReaderPresenter(mockView, mockBaseMode, mockSql, TEST_PATH, false));
        doNothing().when(presenter).initAudioPlayer(anyString());
        doReturn(tempNavigator).when(presenter).createNavigator(any(DaisyBook.class));

        presenter.openBook();

        assertNotNull(presenter.getBook());
    }

    // ========================================================================
    // nextSection / previousSection テスト
    // ========================================================================

    @Test
    public void nextSection_hasNext_incrementsPosition() throws Exception {
        setupPresenterWithBook();

        int initialPosition = presenter.getPositionSection();
        presenter.nextSection();

        assertEquals(initialPosition + 1, presenter.getPositionSection());
    }

    @Test
    public void nextSection_atEnd_callsOnReachedEndOfBook() throws Exception {
        setupPresenterWithBookAtEnd();

        presenter.nextSection();

        verify(mockView).onReachedEndOfBook();
    }

    @Test
    public void previousSection_hasPrevious_decrementsPosition() throws Exception {
        setupPresenterWithBook();
        // まず1つ進める
        presenter.nextSection();
        int positionAfterNext = presenter.getPositionSection();

        presenter.previousSection();

        assertEquals(positionAfterNext - 1, presenter.getPositionSection());
    }

    @Test
    public void previousSection_atBegin_callsOnReachedBeginOfBook() throws Exception {
        setupPresenterWithBookAtBegin();

        presenter.previousSection();

        verify(mockView).onReachedBeginOfBook();
    }

    // ========================================================================
    // togglePlay テスト
    // ========================================================================

    @Test
    public void togglePlay_whenNotPlaying_startsPlayback() throws Exception {
        setupPresenterWithBook();
        when(mockPlayer.isPlaying()).thenReturn(false);

        presenter.togglePlay();

        verify(mockView).showPlayingState();
    }

    @Test
    public void togglePlay_whenPlaying_pausesPlayback() throws Exception {
        setupPresenterWithBook();
        when(mockPlayer.isPlaying()).thenReturn(true);

        presenter.togglePlay();

        verify(mockView).showPausedState();
    }

    // ========================================================================
    // setMediaPlay / setMediaPause テスト
    // ========================================================================

    @Test
    public void setMediaPlay_callsShowPlayingState() throws Exception {
        setupPresenterWithBook();

        presenter.setMediaPlay();

        verify(mockView).showPlayingState();
        assertTrue(presenter.isPlaying());
    }

    @Test
    public void setMediaPause_callsShowPausedState() throws Exception {
        setupPresenterWithBook();

        presenter.setMediaPause();

        verify(mockView).showPausedState();
        assertFalse(presenter.isPlaying());
    }

    // ========================================================================
    // readBookFromIntent テスト
    // ========================================================================

    @Test
    public void readBookFromIntent_nullSection_callsNextSection() throws Exception {
        setupPresenterWithBook();

        presenter.readBookFromIntent(null, -1, "");

        // nextSection が呼ばれると positionSection が増える
        assertEquals(1, presenter.getPositionSection());
    }

    @Test
    public void readBookFromIntent_withCurrentInfo_restoresPosition() throws Exception {
        setupPresenterWithBook();
        CurrentInformation current = new CurrentInformation();
        current.setSection(2);
        current.setTime(5000);
        current.setAudioName("audio.mp3");
        current.setActivity("OtherActivity");
        current.setAtTheEnd(false);
        when(mockSql.getCurrentInformation()).thenReturn(current);

        presenter.readBookFromIntent(null, -1, "");

        // currentからセクション位置を復元する
        verify(mockView, atLeastOnce()).onSectionLoaded();
    }

    // ========================================================================
    // endOfAudio テスト
    // ========================================================================

    @Test
    public void endOfAudio_daisy202_callsNextSection() throws Exception {
        setupPresenterWithBook();
        int initialPosition = presenter.getPositionSection();

        presenter.endOfAudio();

        // DAISY202 なので nextSection が呼ばれる
        assertEquals(initialPosition + 1, presenter.getPositionSection());
    }

    // ========================================================================
    // destroy テスト
    // ========================================================================

    @Test
    public void destroy_releasesPlayer() throws Exception {
        setupPresenterWithBook();

        presenter.destroy();

        assertNull(presenter.getPlayer());
    }

    // ========================================================================
    // ヘルパーメソッド
    // ========================================================================

    /**
     * テスト用にPresenterを構築する。
     * openBook() は呼ばず、モックを直接注入して依存を隔離する。
     */
    private void setupPresenterWithBook() throws Exception {
        // Navigatorのモック設定: nextSection/previousSectionで使用
        Section mockSection = mock(Section.class);
        when(mockSection.getHref()).thenReturn("section1.smil#text1");
        when(mockNavigator.hasNext()).thenReturn(true);
        when(mockNavigator.hasPrevious()).thenReturn(true);
        when(mockNavigator.next()).thenReturn(mockSection);
        when(mockNavigator.previous()).thenReturn(mockSection);

        // getPartsFromSection のモック
        Part mockPart = mock(Part.class);
        Snippet mockSnippet = mock(Snippet.class);
        when(mockSnippet.getText()).thenReturn("テストテキスト");
        List<Snippet> snippets = new ArrayList<>();
        snippets.add(mockSnippet);
        when(mockPart.getSnippets()).thenReturn(snippets);
        List<Audio> audioElements = new ArrayList<>();
        when(mockPart.getAudioElements()).thenReturn(audioElements);

        when(mockBaseMode.getPartsFromSection(any(Section.class), anyString(), anyBoolean()))
                .thenReturn(new Part[]{mockPart});

        // Presenter生成 + モック注入
        presenter = new ReaderPresenter(mockView, mockBaseMode, mockSql, TEST_PATH, true);
        presenter.setBook(mockBook);
        presenter.setNavigator(mockNavigator);
        presenter.setNavigatorOfTableContents(mockNavigator);
        presenter.setPlayer(mockPlayer);
        presenter.setAudioPlayerController(mockAudioPlayer);
    }

    private void setupPresenterWithBookAtEnd() throws Exception {
        Section mockSection = mock(Section.class);
        when(mockSection.getHref()).thenReturn("section1.smil#text1");
        when(mockNavigator.hasNext()).thenReturn(false);
        when(mockNavigator.hasPrevious()).thenReturn(true);

        presenter = new ReaderPresenter(mockView, mockBaseMode, mockSql, TEST_PATH, true);
        presenter.setBook(mockBook);
        presenter.setNavigator(mockNavigator);
        presenter.setNavigatorOfTableContents(mockNavigator);
        presenter.setPlayer(mockPlayer);
        presenter.setAudioPlayerController(mockAudioPlayer);
    }

    private void setupPresenterWithBookAtBegin() throws Exception {
        Section mockSection = mock(Section.class);
        when(mockSection.getHref()).thenReturn("section1.smil#text1");
        when(mockNavigator.hasNext()).thenReturn(true);
        when(mockNavigator.hasPrevious()).thenReturn(false);

        presenter = new ReaderPresenter(mockView, mockBaseMode, mockSql, TEST_PATH, true);
        presenter.setBook(mockBook);
        presenter.setNavigator(mockNavigator);
        presenter.setNavigatorOfTableContents(mockNavigator);
        presenter.setPlayer(mockPlayer);
        presenter.setAudioPlayerController(mockAudioPlayer);
    }
}
