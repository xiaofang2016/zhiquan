package com.zhiquan.sport.db;

import com.zhiquan.sport.store.db.DatabaseTier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DatabasePlan {

    private DatabasePlan() {
    }

    public record DocRequest(String path, Map<String, String> query, String tier) {
    }

    public record Stage(long stageId, int round) {
    }

    public static String yearPath(DatabaseCatalog.Competition competition) {
        return competition.sportsType() == 2
                ? "/team/getBbYearListByCompetitionId"
                : "/team/getFbYearListByCompetitionId";
    }

    public static List<DocRequest> season(DatabaseCatalog.Competition competition, String year) {
        String type = Integer.toString(competition.sportsType());
        String id = Integer.toString(competition.id());
        List<DocRequest> docs = new ArrayList<>();
        docs.add(daily("/team/getCompetitionInfo", Map.of("type", type, "cid", id)));
        docs.add(daily("/team/getHonorHistory", Map.of("cid", id, "type", type)));
        docs.add(daily("/team/getLatelyMatchInfo", Map.of("cid", id, "type", type)));
        docs.add(daily("/team/getCompetitionStageList", Map.of("type", type, "cid", id)));
        if (!year.isBlank()) {
            docs.add(daily("/team/getTeamRankByTeamId", Map.of("type", type, "competitionId", id, "year", year)));
            docs.add(daily("/team/getCompetitionBracket", Map.of("type", type, "cid", id, "year", year)));
        }
        if (competition.sportsType() == 1 && !year.isBlank()) {
            docs.add(daily("/team/getFbCompetitionStats", Map.of("type", type, "cid", id, "year", year)));
            docs.add(daily("/team/getTeamRankByTeamId", Map.of(
                    "type", type, "competitionId", id, "year", year, "isHome", "1")));
            docs.add(daily("/team/getTeamRankByTeamId", Map.of(
                    "type", type, "competitionId", id, "year", year, "isHome", "0")));
        }
        if (competition.sportsType() == 2 && competition.id() == 1 && !year.isBlank()) {
            docs.add(daily("/team/getNbaTeamRank", Map.of("rankType", "1", "year", year)));
            docs.add(daily("/team/getNbaTeamRank", Map.of("rankType", "3", "year", year)));
        }
        if (competition.sportsType() == 2 && competition.id() == 3 && !year.isBlank()) {
            docs.add(daily("/team/getCbaTeamRank", Map.of("year", year)));
        }
        return docs;
    }

    public static List<DocRequest> rounds(DatabaseCatalog.Competition competition, String year, List<Stage> stages) {
        if (year.isBlank() || stages.isEmpty()) {
            return List.of();
        }
        String type = Integer.toString(competition.sportsType());
        String id = Integer.toString(competition.id());
        List<DocRequest> docs = new ArrayList<>();
        for (Stage stage : stages) {
            docs.add(daily("/team/getMatchListByCid", Map.of(
                    "type", type,
                    "cid", id,
                    "year", year,
                    "roundNum", Integer.toString(stage.round()),
                    "stageId", Long.toString(stage.stageId()))));
        }
        return docs;
    }

    public static List<DocRequest> past(DatabaseCatalog.Competition competition, String year) {
        String type = Integer.toString(competition.sportsType());
        String id = Integer.toString(competition.id());
        List<DocRequest> docs = new ArrayList<>();
        docs.add(new DocRequest("/team/getTeamRankByTeamId",
                Map.of("type", type, "competitionId", id, "year", year), DatabaseTier.FROZEN));
        if (competition.sportsType() == 1) {
            docs.add(new DocRequest("/team/getTeamRankByTeamId",
                    Map.of("type", type, "competitionId", id, "year", year, "isHome", "1"), DatabaseTier.FROZEN));
            docs.add(new DocRequest("/team/getTeamRankByTeamId",
                    Map.of("type", type, "competitionId", id, "year", year, "isHome", "0"), DatabaseTier.FROZEN));
        }
        docs.add(new DocRequest("/team/getCompetitionBracket",
                Map.of("type", type, "cid", id, "year", year), DatabaseTier.FROZEN));
        return docs;
    }

    public static List<DocRequest> live(DatabaseCatalog.Competition competition, String year, List<Stage> stages) {
        String type = Integer.toString(competition.sportsType());
        String id = Integer.toString(competition.id());
        List<DocRequest> docs = new ArrayList<>();
        if (!year.isBlank()) {
            docs.add(live("/team/getTeamRankByTeamId", Map.of("type", type, "competitionId", id, "year", year)));
            docs.add(live("/team/getCompetitionBracket", Map.of("type", type, "cid", id, "year", year)));
        }
        docs.add(live("/team/getLatelyMatchInfo", Map.of("cid", id, "type", type)));
        docs.addAll(rounds(competition, year, stages).stream()
                .map(request -> new DocRequest(request.path(), request.query(), DatabaseTier.LIVE))
                .toList());
        return docs;
    }

    private static DocRequest daily(String path, Map<String, String> query) {
        return new DocRequest(path, query, DatabaseTier.DAILY);
    }

    private static DocRequest live(String path, Map<String, String> query) {
        return new DocRequest(path, query, DatabaseTier.LIVE);
    }
}
