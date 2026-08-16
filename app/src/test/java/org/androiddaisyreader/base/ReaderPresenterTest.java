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
    // handleCurrentInformation テスト
    // ========================================================================

    @Test
    public void handleCurrentInformation_playerNull_noException() throws Exception {
        setupPresenterWithBook();
        // player を null に設定（TTS読み上げモード時を想定）
        presenter.setPlayer(null);

        // createCurrentInformation のモック設定
        CurrentInformation created = new CurrentInformation();
        when(mockBaseMode.createCurrentInformation(
                anyString(), anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(created);

        // NPE にならないことを確認
        presenter.handleCurrentInformation();

        verify(mockBaseMode).createCurrentInformation(
                eq(""), eq("TestActivity"), eq(0), eq(0), eq(false));
        verify(mockSql).addCurrentInformation(created);
    }

    @Test
    public void handleCurrentInformation_withCurrent_callsUpdate() throws Exception {
        setupPresenterWithBook();
        when(mockPlayer.getCurrentPosition()).thenReturn(12345);
        CurrentInformation current = new CurrentInformation();
        current.setActivity("TestActivity");
        presenter.setCurrent(current);

        CurrentInformation updated = new CurrentInformation();
        when(mockBaseMode.updateCurrentInformation(
                any(CurrentInformation.class), anyString(), anyString(),
                anyInt(), anyInt(), anyInt(), anyBoolean())).thenReturn(updated);

        presenter.handleCurrentInformation();

        verify(mockBaseMode).updateCurrentInformation(
                eq(current), eq(""), eq("TestActivity"), eq(0), eq(0), eq(12345), eq(false));
        verify(mockSql).updateCurrentInformation(updated);
    }

    // ========================================================================
    // nextSentence / previousSentence テスト
    // ========================================================================

    @Test
    public void nextSentence_midSection_incrementsPosition() throws Exception {
        setupPresenterWithBookAndAudio();

        int initial = presenter.getPositionSentence();
        presenter.nextSentence();

        assertEquals(initial + 1, presenter.getPositionSentence());
        verify(mockPlayer).seekTo(1000);
        verify(mockView).onSentenceChanged(1);
    }

    @Test
    public void nextSentence_atEnd_callsNextSection() throws Exception {
        setupPresenterWithBookAndAudio();
        // セクション末尾まで進める
        presenter.setPositionSentence(2);

        presenter.nextSentence();

        // nextSection が呼ばれて positionSection が増える
        assertEquals(1, presenter.getPositionSection());
    }

    @Test
    public void previousSentence_midSection_decrementsPosition() throws Exception {
        setupPresenterWithBookAndAudio();
        presenter.setPositionSentence(2);

        presenter.previousSentence();

        assertEquals(1, presenter.getPositionSentence());
        verify(mockPlayer).seekTo(1000);
        verify(mockView).onSentenceChanged(1);
    }

    @Test
    public void previousSentence_atBegin_callsPreviousSection() throws Exception {
        setupPresenterWithBookAndAudio();
        presenter.setPositionSentence(0);
        when(mockNavigator.hasPrevious()).thenReturn(true);

        presenter.previousSentence();

        // previousSection が呼ばれて positionSection が減る
        assertEquals(-1, presenter.getPositionSection());
    }

    // ========================================================================
    // TTS 読み上げテスト
    // ========================================================================

    @Test
    public void startReadAloud_emptyText_doesNothing() throws Exception {
        setupPresenterWithBook();

        presenter.startReadAloud();

        assertFalse(presenter.isReadAloudMode());
        verify(mockView, never()).speakSentence(anyString(), anyInt());
    }

    @Test
    public void startReadAloud_withText_speaksFirstSentence() throws Exception {
        setupPresenterWithBookAndAudio();

        presenter.startReadAloud();

        assertTrue(presenter.isReadAloudMode());
        assertEquals(0, presenter.getPositionSentence());
        verify(mockView).applyReadAloudSettings();
        verify(mockView).highlightSentence(0);
        verify(mockView).speakSentence(anyString(), eq(0));
    }

    @Test
    public void onUtteranceCompleted_midSection_advancesToNext() throws Exception {
        setupPresenterWithBookAndAudio();
        presenter.startReadAloud();

        presenter.onUtteranceCompleted(0);

        assertEquals(1, presenter.getPositionSentence());
        verify(mockView).speakSentence(anyString(), eq(1));
    }

    @Test
    public void onUtteranceCompleted_atEnd_callsNextSection() throws Exception {
        setupPresenterWithBookAndAudio();
        presenter.startReadAloud();

        // 最後のセンテンス完了
        presenter.onUtteranceCompleted(2);

        // nextSection が呼ばれて positionSection が増える
        assertEquals(1, presenter.getPositionSection());
    }

    @Test
    public void nextSentence_inReadAloudMode_speaksNext() throws Exception {
        setupPresenterWithBookAndAudio();
        presenter.startReadAloud();

        presenter.nextSentence();

        assertEquals(1, presenter.getPositionSentence());
        verify(mockView, atLeast(1)).stopReadAloud();
    }

    @Test
    public void togglePlay_inReadAloudMode_pauses() throws Exception {
        setupPresenterWithBookAndAudio();
        presenter.startReadAloud();
        presenter.setPlaying(true);

        presenter.togglePlay();

        assertFalse(presenter.isPlaying());
        verify(mockView).showPausedState();
    }

    // ========================================================================
    // ヘルパーメソッド（Audio付き）
    // ========================================================================

    /**
     * テスト用にPresenterを構築する（listStringText/listTimeBegin付き）。
     * nextSentence / TTS テスト用。
     */
    private void setupPresenterWithBookAndAudio() throws Exception {
        Section mockSection = mock(Section.class);
        when(mockSection.getHref()).thenReturn("section1.smil#text1");
        when(mockNavigator.hasNext()).thenReturn(true);
        when(mockNavigator.hasPrevious()).thenReturn(true);
        when(mockNavigator.next()).thenReturn(mockSection);
        when(mockNavigator.previous()).thenReturn(mockSection);

        // 3つのPartを作成（各Part: テキスト1文 + Audio1つ）
        // "テスト文1" / "テスト文2" / "テスト文3" を個別Partにして
        // それぞれにAudio付き → listTimeBeginが3要素になる
        Part mockPart1 = mock(Part.class);
        Snippet mockSnippet1 = mock(Snippet.class);
        when(mockSnippet1.getText()).thenReturn("テスト文1");
        List<Snippet> snippets1 = new ArrayList<>();
        snippets1.add(mockSnippet1);
        when(mockPart1.getSnippets()).thenReturn(snippets1);
        Audio audio1 = mock(Audio.class);
        when(audio1.getClipBegin()).thenReturn(0);
        when(audio1.getClipEnd()).thenReturn(1000);
        when(audio1.getAudioFilename()).thenReturn("audio01.mp3");
        List<Audio> audioElements1 = new ArrayList<>();
        audioElements1.add(audio1);
        when(mockPart1.getAudioElements()).thenReturn(audioElements1);

        Part mockPart2 = mock(Part.class);
        Snippet mockSnippet2 = mock(Snippet.class);
        when(mockSnippet2.getText()).thenReturn("テスト文2");
        List<Snippet> snippets2 = new ArrayList<>();
        snippets2.add(mockSnippet2);
        when(mockPart2.getSnippets()).thenReturn(snippets2);
        Audio audio2 = mock(Audio.class);
        when(audio2.getClipBegin()).thenReturn(1000);
        when(audio2.getClipEnd()).thenReturn(2000);
        when(audio2.getAudioFilename()).thenReturn("audio01.mp3");
        List<Audio> audioElements2 = new ArrayList<>();
        audioElements2.add(audio2);
        when(mockPart2.getAudioElements()).thenReturn(audioElements2);

        Part mockPart3 = mock(Part.class);
        Snippet mockSnippet3 = mock(Snippet.class);
        when(mockSnippet3.getText()).thenReturn("テスト文3");
        List<Snippet> snippets3 = new ArrayList<>();
        snippets3.add(mockSnippet3);
        when(mockPart3.getSnippets()).thenReturn(snippets3);
        Audio audio3 = mock(Audio.class);
        when(audio3.getClipBegin()).thenReturn(2000);
        when(audio3.getClipEnd()).thenReturn(3000);
        when(audio3.getAudioFilename()).thenReturn("audio01.mp3");
        List<Audio> audioElements3 = new ArrayList<>();
        audioElements3.add(audio3);
        when(mockPart3.getAudioElements()).thenReturn(audioElements3);

        when(mockBaseMode.getPartsFromSection(any(Section.class), anyString(), anyBoolean()))
                .thenReturn(new Part[]{mockPart1, mockPart2, mockPart3});

        presenter = new ReaderPresenter(mockView, mockBaseMode, mockSql, TEST_PATH, true);
        presenter.setBook(mockBook);
        presenter.setNavigator(mockNavigator);
        presenter.setNavigatorOfTableContents(mockNavigator);
        presenter.setPlayer(mockPlayer);
        presenter.setAudioPlayerController(mockAudioPlayer);

        // onNavigationNext を呼んで listStringText / listTimeBegin を初期化
        presenter.onNavigationNext(mockSection);
    }

    // ========================================================================
    // 既存ヘルパーメソッド
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
