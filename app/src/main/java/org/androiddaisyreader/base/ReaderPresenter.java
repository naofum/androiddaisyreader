package org.androiddaisyreader.base;

import android.media.MediaPlayer;

import org.androiddaisyreader.controller.AudioPlayerController;
import org.androiddaisyreader.model.Audio;
import org.androiddaisyreader.model.BookContext;
import org.androiddaisyreader.model.CurrentInformation;
import org.androiddaisyreader.model.DaisyBook;
import org.androiddaisyreader.model.DaisySnippet;
import org.androiddaisyreader.model.Navigable;
import org.androiddaisyreader.model.Navigator;
import org.androiddaisyreader.model.Part;
import org.androiddaisyreader.model.Section;
import org.androiddaisyreader.model.Snippet;
import org.androiddaisyreader.apps.PrivateException;
import org.androiddaisyreader.sqlite.SQLiteCurrentInformationHelper;
import org.androiddaisyreader.utils.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MVP パターンの Presenter。
 * 両Mode共通のナビゲーション・再生制御ロジックを持つ。
 * UI固有の処理は ReaderView インターフェース経由で委譲する。
 */
public class ReaderPresenter {

    private final ReaderView view;
    private final DaisyEbookReaderBaseMode baseMode;
    private final SQLiteCurrentInformationHelper sql;

    // --- 状態 ---
    private DaisyBook book;
    private Navigator navigator;
    private Navigator navigatorOfTableContents;
    private AudioPlayerController audioPlayer;
    private MediaPlayer player;
    private CurrentInformation current;

    private String path;
    private boolean isFormat202;
    private List<String> listId;
    private List<Audio> listAudio;
    private int countAudio = 0;

    private boolean isFirstNext = false;
    private boolean isFirstPrevious = true;
    private boolean isEndOf = false;
    private boolean isRunnable = true;
    private boolean isPlaying = false;

    private int positionSection = 0;
    private int positionSentence = 0;
    private int time = -1;
    private int timePause = 0;

    // --- セクションデータ ---
    private List<String> listStringText = new ArrayList<>();
    private List<Integer> listTimeBegin = new ArrayList<>();
    private List<Integer> listTimeEnd = new ArrayList<>();
    private Map<String, List<Integer>> hashMapBegin = new LinkedHashMap<>();
    private Map<String, List<Integer>> hashMapEnd = new LinkedHashMap<>();

    private static final int TIME_FOR_PROCESS = 400;

    public ReaderPresenter(ReaderView view, DaisyEbookReaderBaseMode baseMode,
                           SQLiteCurrentInformationHelper sql, String path, boolean isFormat202) {
        this(view, baseMode, sql, path, isFormat202, null);
    }

    public ReaderPresenter(ReaderView view, DaisyEbookReaderBaseMode baseMode,
                           SQLiteCurrentInformationHelper sql, String path, boolean isFormat202,
                           java.util.concurrent.ExecutorService dbExecutor) {
        this.view = view;
        this.baseMode = baseMode;
        this.sql = sql;
        this.path = path;
        this.isFormat202 = isFormat202;
        this.dbExecutor = dbExecutor;
    }

    private final java.util.concurrent.ExecutorService dbExecutor;

    /**
     * DB更新をバックグラウンドで実行する（fire-and-forget）。
     */
    private void updateCurrentInformationAsync(final CurrentInformation info) {
        if (info == null) return;
        if (dbExecutor != null) {
            dbExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    sql.updateCurrentInformation(info);
                }
            });
        } else {
            sql.updateCurrentInformation(info);
        }
    }

    // ========================================================================
    // Getters (Activity側で必要な参照)
    // ========================================================================

    public DaisyBook getBook() { return book; }
    public Navigator getNavigator() { return navigator; }
    public Navigator getNavigatorOfTableContents() { return navigatorOfTableContents; }
    public MediaPlayer getPlayer() { return player; }
    public AudioPlayerController getAudioPlayer() { return audioPlayer; }
    public CurrentInformation getCurrent() { return current; }
    public void setCurrent(CurrentInformation current) { this.current = current; }
    public int getPositionSection() { return positionSection; }
    public int getPositionSentence() { return positionSentence; }
    public void setPositionSentence(int pos) { this.positionSentence = pos; }
    public boolean isEndOf() { return isEndOf; }
    public boolean isRunnable() { return isRunnable; }
    public void setRunnable(boolean runnable) { this.isRunnable = runnable; }
    public boolean isPlaying() { return isPlaying; }
    public void setPlaying(boolean playing) { this.isPlaying = playing; }
    public List<String> getListStringText() { return listStringText; }
    public List<Integer> getListTimeBegin() { return listTimeBegin; }
    public List<Integer> getListTimeEnd() { return listTimeEnd; }
    public Map<String, List<Integer>> getHashMapBegin() { return hashMapBegin; }
    public Map<String, List<Integer>> getHashMapEnd() { return hashMapEnd; }
    public List<Audio> getListAudio() { return listAudio; }
    public int getCountAudio() { return countAudio; }
    public void setCountAudio(int count) { this.countAudio = count; }
    public String getPath() { return path; }
    public boolean isFormat202() { return isFormat202; }
    public int getTimePause() { return timePause; }
    public void setTimePause(int timePause) { this.timePause = timePause; }
    public boolean isFirstNext() { return isFirstNext; }
    public boolean isFirstPrevious() { return isFirstPrevious; }

    // ========================================================================
    // Package-private setters for testability
    // ========================================================================

    void setBook(DaisyBook book) { this.book = book; }
    void setNavigator(Navigator navigator) { this.navigator = navigator; }
    void setNavigatorOfTableContents(Navigator nav) { this.navigatorOfTableContents = nav; }
    void setAudioPlayerController(AudioPlayerController audioPlayer) { this.audioPlayer = audioPlayer; }
    void setPlayer(MediaPlayer player) { this.player = player; }

    // ========================================================================
    // openBook - 本を開く
    // ========================================================================

    public void openBook() {
        try {
            if (isFormat202) {
                book = baseMode.openBook202();
            } else {
                book = baseMode.openBook30();
                path = baseMode.getPathExactlyDaisy30(path);
                Navigator temp = createNavigator(book);
                listId = new ArrayList<>();
                while (temp.hasNext()) {
                    Section n = (Section) temp.next();
                    if (splitHref(n.getHref()).length > 1) {
                        listId.add(splitHref(n.getHref())[1]);
                    }
                }
            }

            initAudioPlayer(path);
            navigatorOfTableContents = createNavigator(book);
            navigator = navigatorOfTableContents;
        } catch (PrivateException ex) {
            if (!view.isActivityFinishing()) {
                view.showErrorDialog(ex);
            }
        }
    }

    /**
     * Audio player の初期化。テスト時はオーバーライドまたはセッターで差し替え可能。
     */
    protected void initAudioPlayer(String path) throws PrivateException {
        BookContext bookContext = baseMode.getBookContext(path);
        org.androiddaisyreader.player.AndroidAudioPlayer androidAudioPlayer =
                new org.androiddaisyreader.player.AndroidAudioPlayer(bookContext);
        androidAudioPlayer.addCallbackListener(audioCallbackListener);
        audioPlayer = new AudioPlayerController(androidAudioPlayer);
        player = androidAudioPlayer.getCurrentPlayer();
    }

    /**
     * Navigator の生成。テスト時はオーバーライドで差し替え可能。
     */
    protected Navigator createNavigator(DaisyBook book) {
        return new Navigator(book);
    }

    // ========================================================================
    // readBook - 読書開始
    // ========================================================================

    public void readBook() {
        String section = "";
        current = sql.getCurrentInformation();
        String audioFileName = "";
        try {
            if (current != null && current.getActivity().equals(view.getActivityName())) {
                current.setAtTheEnd(false);
                updateCurrentInformationAsync(current);
            }
            if (current != null && !current.getActivity().equals(view.getActivityName())) {
                section = String.valueOf(current.getSection());
                time = current.getTime();
                audioFileName = current.getAudioName();
                positionSentence = 0;
                current.setActivity(view.getActivityName());
                updateCurrentInformationAsync(current);
            } else {
                // サブクラスでIntent情報を設定するためnullになる場合がある
                return;
            }
            if (section != null) {
                int countLoop = parseSectionSafely(section) - positionSection;
                Navigable n = getNavigable(countLoop);
                if (n instanceof Section) {
                    onNavigationNext((Section) n);
                }
                playBookmarkOfDaisy30(audioFileName);
            } else {
                togglePlay();
            }
        } catch (Exception e) {
            if (!view.isActivityFinishing()) {
                view.showErrorDialog(e);
            }
        }
    }

    /**
     * Intent情報からreadBookを実行する（onCreate用）
     */
    public void readBookFromIntent(String sectionStr, int intentTime, String audioFileName) {
        current = sql.getCurrentInformation();
        try {
            if (current != null && current.getActivity().equals(view.getActivityName())) {
                current.setAtTheEnd(false);
                updateCurrentInformationAsync(current);
            }
            if (current != null && !current.getActivity().equals(view.getActivityName())) {
                String section = String.valueOf(current.getSection());
                time = current.getTime();
                audioFileName = current.getAudioName();
                positionSentence = 0;
                current.setActivity(view.getActivityName());
                updateCurrentInformationAsync(current);
                sectionStr = section;
            } else {
                time = intentTime;
            }
            if (sectionStr != null) {
                int countLoop = parseSectionSafely(sectionStr) - positionSection;
                Navigable n = getNavigable(countLoop);
                if (n instanceof Section) {
                    onNavigationNext((Section) n);
                }
                playBookmarkOfDaisy30(audioFileName);
            } else {
                // 初回起動: 最初のセクションに移動して再生開始
                nextSection();
            }
        } catch (Exception e) {
            if (!view.isActivityFinishing()) {
                view.showErrorDialog(e);
            }
        }
    }

    // ========================================================================
    // ナビゲーション
    // ========================================================================

    public Navigable getNavigable(int countLoop) {
        Navigable n = null;
        if (countLoop >= 0) {
            n = nextSectionByCountLoop(countLoop);
        } else {
            n = previousSectionByCountLoop(Math.abs(countLoop));
        }
        return n;
    }

    private Navigable nextSectionByCountLoop(int countLoop) {
        Navigable n = null;
        for (int j = 0; j < countLoop; j++) {
            n = navigator.next();
            if (current != null && !current.getActivity().equals(view.getActivityName())) {
                n = navigator.next();
                isFirstPrevious = true;
                isFirstNext = false;
            }
            positionSection += 1;
        }
        return n;
    }

    private Navigable previousSectionByCountLoop(int countLoop) {
        Navigable n = null;
        for (int j = 0; j < countLoop; j++) {
            n = navigator.previous();
            if (current != null && !current.getActivity().equals(view.getActivityName())) {
                n = navigator.previous();
                isFirstPrevious = false;
                isFirstNext = true;
            }
            positionSection -= 1;
        }
        return n;
    }

    public void nextSection() {
        if (navigator.hasNext()) {
            positionSentence = 0;
            positionSection += 1;
            isEndOf = false;
            Section section = (Section) navigator.next();
            onNavigationNext(section);
        } else {
            view.onReachedEndOfBook();
        }
    }

    public void previousSection() {
        if (navigator.hasPrevious()) {
            positionSentence = 0;
            positionSection -= 1;
            Section section = (Section) navigator.previous();
            onNavigationNext(section);
        } else {
            view.onReachedBeginOfBook();
        }
    }

    // ========================================================================
    // センテンス移動
    // ========================================================================

    public void nextSentence() {
        if (isFormat202) {
            nextSentenceDaisy202();
        } else {
            nextSentenceDaisy30();
        }
    }

    public void previousSentence() {
        if (isFormat202) {
            previousSentenceDaisy202();
        } else {
            previousSentenceDaisy30();
        }
    }

    private void nextSentenceDaisy202() {
        if (positionSentence < listStringText.size() - 1) {
            positionSentence++;
            player.seekTo(listTimeBegin.get(positionSentence));
            view.onSentenceChanged(positionSentence);
        } else {
            nextSection();
        }
    }

    private void nextSentenceDaisy30() {
        if (positionSentence < listStringText.size() - 1) {
            positionSentence++;
            player.seekTo(listTimeBegin.get(positionSentence));
            view.onSentenceChanged(positionSentence);
        } else {
            // セクション末尾に達した場合
            nextSection();
        }
    }

    private void previousSentenceDaisy202() {
        if (positionSentence > 0) {
            positionSentence--;
            player.seekTo(listTimeBegin.get(positionSentence));
            view.onSentenceChanged(positionSentence);
        } else {
            previousSection();
        }
    }

    private void previousSentenceDaisy30() {
        if (positionSentence > 0) {
            positionSentence--;
            player.seekTo(listTimeBegin.get(positionSentence));
            view.onSentenceChanged(positionSentence);
        } else {
            previousSection();
        }
    }

    // ========================================================================
    // 再生制御
    // ========================================================================

    public void playFileSegmentForDaisy30() {
        if (!isFormat202 && listAudio != null && listAudio.size() > countAudio) {
            countAudio++;
            if (countAudio < listAudio.size()) {
                audioPlayer.playFileSegment(listAudio.get(countAudio));
            } else {
                nextSection();
            }
        }
    }

    public void playBookmarkOfDaisy30(String audioFileName) {
        if (!isFormat202 && listAudio != null) {
            for (int i = 0; i < listAudio.size(); i++) {
                Audio audio = listAudio.get(i);
                if (audio.getAudioFilename().equals(audioFileName)) {
                    countAudio = i;
                    audioPlayer.playFileSegment(audio);
                    break;
                }
            }
            if (time != -1) {
                player.seekTo(time);
                time = -1;
            }
            if (current != null) {
                updateCurrentInformationAsync(current);
                if (current.getPlaying()) {
                    setMediaPlay();
                } else {
                    setMediaPause();
                }
            }
        }
    }

    public void togglePlay() {
        if (player != null && player.isPlaying()) {
            setMediaPause();
        } else {
            setMediaPlay();
        }
    }

    public void setMediaPlay() {
        if (player != null) {
            player.start();
            isRunnable = true;
            isPlaying = true;
            if (current != null) {
                current.setPlaying(true);
                updateCurrentInformationAsync(current);
            }
            view.showPlayingState();
        }
    }

    public void setMediaPause() {
        if (player != null && player.isPlaying()) {
            timePause = player.getCurrentPosition();
            player.pause();
        }
        isRunnable = false;
        isPlaying = false;
        if (current != null) {
            current.setPlaying(false);
            updateCurrentInformationAsync(current);
        }
        view.showPausedState();
    }

    // ========================================================================
    // endOfAudio コールバック
    // ========================================================================

    public void endOfAudio() {
        if (isFormat202) {
            nextSection();
        } else {
            playFileSegmentForDaisy30();
        }
    }

    // ========================================================================
    // NavigationListener.onNext 共通部分
    // ========================================================================

    public void onNavigationNext(Section section) {
        try {
            Part[] parts;
            if (isFormat202) {
                parts = baseMode.getPartsFromSection(section, path, isFormat202);
            } else {
                parts = baseMode.getPartsFromSectionDaisy30(section, path, isFormat202,
                        listId, positionSection, baseMode.getBookContext(path));
            }
            loadSnippetsAndAudio(parts);
            view.onSectionLoaded();
        } catch (Exception e) {
            if (!view.isActivityFinishing()) {
                view.showErrorDialog(e);
            }
        }
    }

    private void loadSnippetsAndAudio(Part[] parts) {
        listStringText = new ArrayList<>();
        listTimeBegin = new ArrayList<>();
        listTimeEnd = new ArrayList<>();
        hashMapBegin = new LinkedHashMap<>();
        hashMapEnd = new LinkedHashMap<>();
        List<Integer> listClipBegin = new ArrayList<>();
        List<Integer> listClipEnd = new ArrayList<>();
        String fileName = null;
        StringBuilder content = new StringBuilder();
        String imageSrc = null;

        for (Part part : parts) {
            // テキスト・画像の抽出
            int sizeOfPart = part.getSnippets().size();
            for (int i = 0; i < sizeOfPart; i++) {
                Snippet snippet = part.getSnippets().get(i);
                String text = snippet.getText().toString();
                listStringText.add(text);
                content.append(text).append("\n");
                if (snippet instanceof DaisySnippet) {
                    String img = ((DaisySnippet) snippet).getImg();
                    if (img != null && !img.isEmpty()) {
                        imageSrc = img;
                    }
                }
            }

            // オーディオタイミングの抽出
            List<Audio> audioElements = part.getAudioElements();
            int audioElementsSize = audioElements.size();
            if (audioElementsSize > 0) {
                Audio audio = audioElements.get(0);
                listTimeBegin.add(audio.getClipBegin());
                listTimeEnd.add(audioElements.get(audioElementsSize - 1).getClipEnd());
                if (fileName == null || !fileName.equals(audio.getAudioFilename())) {
                    hashMapBegin.put(fileName, listClipBegin);
                    hashMapEnd.put(fileName, listClipEnd);
                    listClipBegin = new ArrayList<>();
                    listClipEnd = new ArrayList<>();
                    fileName = audio.getAudioFilename();
                }
                listClipBegin.add(audio.getClipBegin());
                listClipEnd.add(audioElements.get(audioElementsSize - 1).getClipEnd());
            }
        }
        hashMapBegin.put(fileName, listClipBegin);
        hashMapEnd.put(fileName, listClipEnd);

        // DAISY202: オーディオ再生
        if (isFormat202) {
            try {
                for (Part part : parts) {
                    for (Audio audioSegment : part.getAudioElements()) {
                        audioPlayer.playFileSegment(audioSegment);
                    }
                }
            } catch (Exception e) {
                // audio not found
            }
        } else {
            // DAISY30: オーディオリスト構築
            String audioFileName = "";
            listAudio = new ArrayList<>();
            countAudio = 0;
            for (Part part : parts) {
                if (part.getAudioElements().size() > 0) {
                    Audio audioSegment = part.getAudioElements().get(0);
                    if (!audioSegment.getAudioFilename().equals(audioFileName)) {
                        listAudio.add(audioSegment);
                        audioFileName = audioSegment.getAudioFilename();
                    }
                }
            }
            if (listAudio.size() > 0) {
                audioPlayer.playFileSegment(listAudio.get(0));
            }
        }

        // View にコンテンツ通知
        view.displayContent(content.toString());
        if (imageSrc != null) {
            view.displayImage(imageSrc);
        }

        // seek to time (モード切替時)
        if (listTimeEnd.size() > 0 && isFormat202 && time != -1) {
            player.seekTo(time);
            time = -1;
        }
    }

    // ========================================================================
    // 現在情報の保存
    // ========================================================================

    public void handleCurrentInformation() {
        String audioName = "";
        if (!isFormat202 && listAudio != null && !listAudio.isEmpty()) {
            audioName = listAudio.get(countAudio).getAudioFilename();
        }
        String activity = view.getActivityName();
        CurrentInformation currentInformation;
        if (current == null) {
            currentInformation = baseMode.createCurrentInformation(audioName, activity,
                    positionSection, player.getCurrentPosition(), isPlaying);
            sql.addCurrentInformation(currentInformation);
        } else {
            currentInformation = baseMode.updateCurrentInformation(current, audioName, activity,
                    positionSection, positionSentence, player.getCurrentPosition(), isPlaying);
            sql.updateCurrentInformation(currentInformation);
        }
    }

    // ========================================================================
    // AudioCallbackListener
    // ========================================================================

    private final org.androiddaisyreader.AudioCallbackListener audioCallbackListener =
            new org.androiddaisyreader.AudioCallbackListener() {
                @Override
                public void endOfAudio() {
                    ReaderPresenter.this.endOfAudio();
                }
            };

    // ========================================================================
    // ユーティリティ
    // ========================================================================

    private String[] splitHref(String href) {
        return href.split("#");
    }

    private int parseSectionSafely(String section) {
        if (section == null || section.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(section.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void refreshNavigator() {
        if (book != null) {
            navigatorOfTableContents = new Navigator(book);
        }
    }

    public void destroy() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
                player.release();
                player = null;
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
