package com.turtlesoup.history;

import org.springframework.stereotype.Service;

@Service
public class PlayHistoryService {

    private final PlayHistoryRepository repository;

    public PlayHistoryService(PlayHistoryRepository repository) {
        this.repository = repository;
    }

    public void recordSolve(Long puzzleId, GameMode mode, String nickname, int questionCount) {
        repository.save(new PlayHistory(puzzleId, mode, nickname, questionCount, true));
    }
}
