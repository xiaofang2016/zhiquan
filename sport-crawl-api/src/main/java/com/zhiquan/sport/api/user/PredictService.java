package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.api.speak.SpeakService;
import com.zhiquan.sport.store.chat.ChatTranscript;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.S3JsonStore;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PredictService {

    static final String KEY = "predict/bets.json";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserAuthService auth;
    private final SportUserRepository users;
    private final S3JsonStore objects;
    private final ObjectMapper json;
    private final RestTemplate http;
    private final String tipUrl;
    private final String matchBase;

    public PredictService(UserAuthService auth, SportUserRepository users, S3JsonStore objects, ObjectMapper json,
                          @Value("${sport.tip.url:https://tip.kzbzcq.com/tip}") String tipUrl,
                          @Value("${sport.community.base-url:https://kzb001.net}") String matchBase) {
        this.auth = auth;
        this.users = users;
        this.objects = objects;
        this.json = json;
        this.tipUrl = tipUrl;
        String base = matchBase.endsWith("/") ? matchBase.substring(0, matchBase.length() - 1) : matchBase;
        this.matchBase = base;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.http = new RestTemplate(factory);
    }

    public Map<String, Object> bet(String token, String midRaw, Integer typeRaw, String marketRaw, Integer betvalue, Integer stakeRaw, String clientRaw) {
        SportUser user = auth.account(token);
        String mid = ChatTranscript.matchId(midRaw);
        int type = PredictRules.sport(typeRaw);
        String market = PredictRules.market(marketRaw);
        int stake = PredictRules.stake(stakeRaw);
        String clientId = PredictRules.clientId(clientRaw);
        if (betvalue == null) {
            throw new IllegalArgumentException("选项不正确");
        }
        PredictRules.Quote quote = PredictRules.readQuote(get(tipUrl + "?mid=" + mid + "&type=" + type), market, betvalue);
        LocalDateTime now = LocalDateTime.now(MissionRules.ZONE);
        for (int attempt = 0; attempt < 2; attempt++) {
            Held held = hold(user.getPhone());
            if (!clientId.isEmpty()) {
                for (PredictRules.Bet existing : held.bets()) {
                    if (clientId.equals(existing.clientId())) {
                        return placed(existing, held.mission().balance());
                    }
                }
            }
            int pending = 0;
            for (PredictRules.Bet row : held.bets()) {
                if ("pending".equals(row.status())) {
                    pending++;
                }
            }
            if (pending >= 20) {
                throw new IllegalArgumentException("未开奖的预测太多");
            }
            PredictRules.Bet bet = new PredictRules.Bet("p" + UUID.randomUUID().toString().replace("-", ""),
                    user.getUserId(), user.getPhone(), SpeakService.nickOf(user), mid, type, market, betvalue,
                    quote.label(), quote.line(), quote.odds(), stake, "pending", 0, 0, now.format(STAMP), "", clientId);
            MissionRules.State nextMission = MissionRules.change(held.mission(), "预测投注", -stake, now);
            List<PredictRules.Bet> nextBets = new ArrayList<>();
            nextBets.add(bet);
            nextBets.addAll(held.bets());
            trim(nextBets, 100);
            try {
                users.saveMissionAndPredict(user.getPhone(), held.missionRaw(), encodeMission(nextMission), held.predictRaw(), encodeBets(nextBets));
            } catch (ConditionalCheckFailedException ex) {
                if (attempt == 1) {
                    throw new IllegalArgumentException("请稍后再试");
                }
                continue;
            }
            try {
                appendGlobal(bet);
            } catch (RuntimeException ex) {
                try {
                    undo(user.getPhone(), bet, stake, now);
                } catch (RuntimeException ignored) {
                    throw new IllegalArgumentException("请稍后再试");
                }
                throw new IllegalArgumentException("投注失败，音浪已退回");
            }
            return placed(bet, nextMission.balance());
        }
        throw new IllegalArgumentException("请稍后再试");
    }

    public Map<String, Object> mine(String token) {
        SportUser user = auth.account(token);
        LocalDateTime now = LocalDateTime.now(MissionRules.ZONE);
        try {
            settlePhone(user.getPhone(), new LinkedHashMap<>(), now);
            project(user.getPhone());
        } catch (ConditionalCheckFailedException | IllegalStateException ignored) {
            // 这次没写成，下次打开再结算。
        }
        Held held = hold(user.getPhone());
        int total = 0;
        int win = 0;
        int profit = 0;
        List<Map<String, Object>> records = new ArrayList<>();
        for (PredictRules.Bet bet : held.bets()) {
            records.add(viewBet(bet));
            if ("pending".equals(bet.status()) || "refund".equals(bet.status())) {
                continue;
            }
            total++;
            profit += bet.profit();
            if ("win".equals(bet.status()) || "half-win".equals(bet.status())) {
                win++;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", held.mission().balance());
        data.put("total", total);
        data.put("win", win);
        data.put("rate", total == 0 ? "0%" : (win * 100 / total) + "%");
        data.put("profit", profit);
        data.put("records", records);
        return data;
    }

    public Map<String, Object> rank(String token, String periodRaw, String boardRaw) {
        SportUser user = auth.account(token);
        String period = periodRaw == null || periodRaw.isBlank() ? "day" : periodRaw.trim();
        String board = boardRaw == null || boardRaw.isBlank() ? "profit" : boardRaw.trim();
        settleBoard();
        PredictRules.BoardView view = PredictRules.rank(loadGlobal(), board, period, LocalDate.now(MissionRules.ZONE), user.getUserId());
        List<Map<String, Object>> records = new ArrayList<>();
        for (PredictRules.RankRow row : view.records()) {
            records.add(viewRank(row));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period", period);
        data.put("board", board);
        data.put("records", records);
        data.put("mine", viewRank(view.mine()));
        return data;
    }

    private void settleBoard() {
        List<PredictRules.Bet> bets = loadGlobal();
        Set<String> phones = new LinkedHashSet<>();
        for (PredictRules.Bet bet : bets) {
            if ("pending".equals(bet.status()) && bet.phone() != null && !bet.phone().isBlank()) {
                phones.add(bet.phone());
            }
        }
        Map<String, PredictRules.MatchEnd> ends = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now(MissionRules.ZONE);
        for (String phone : phones) {
            try {
                settlePhone(phone, ends, now);
            } catch (ConditionalCheckFailedException ex) {
                settlePhone(phone, ends, now);
            }
        }
        if (!phones.isEmpty()) {
            projectPhones(phones);
        }
    }

    private boolean settlePhone(String phone, Map<String, PredictRules.MatchEnd> ends, LocalDateTime now) {
        for (int attempt = 0; attempt < 2; attempt++) {
            Held held = hold(phone);
            List<PredictRules.Bet> next = new ArrayList<>();
            MissionRules.State mission = held.mission();
            boolean changed = false;
            for (PredictRules.Bet bet : held.bets()) {
                if (!"pending".equals(bet.status())) {
                    next.add(bet);
                    continue;
                }
                PredictRules.MatchEnd end = ends.computeIfAbsent(bet.mid() + "#" + bet.type(), key -> fetchMatch(bet.mid(), bet.type()));
                if (end == null || "open".equals(end.kind())) {
                    next.add(bet);
                    continue;
                }
                PredictRules.Outcome outcome = "void".equals(end.kind())
                        ? PredictRules.refund(bet)
                        : PredictRules.settle(bet, end.home(), end.away());
                if (outcome.returned() > 0) {
                    mission = MissionRules.change(mission, payoutTitle(outcome.status()), outcome.returned(), now);
                }
                next.add(finish(bet, outcome, now.format(STAMP)));
                changed = true;
            }
            if (!changed) {
                return false;
            }
            try {
                users.saveMissionAndPredict(phone, held.missionRaw(), encodeMission(mission), held.predictRaw(), encodeBets(next));
                return true;
            } catch (ConditionalCheckFailedException ex) {
                if (attempt == 1) {
                    throw ex;
                }
            }
        }
        return false;
    }

    private PredictRules.MatchEnd fetchMatch(String mid, int type) {
        String url = matchBase + "/prod-api/match/detail?mid=" + mid + "&type=" + type + "&isnew=1&pid=4&langtype=zh&zoneId=Asia/Shanghai";
        JsonNode body = get(url);
        if (body == null) {
            return new PredictRules.MatchEnd("open", 0, 0);
        }
        return PredictRules.readMatch(body);
    }

    private void undo(String phone, PredictRules.Bet bet, int stake, LocalDateTime now) {
        removeGlobal(bet.id());
        for (int attempt = 0; attempt < 2; attempt++) {
            Held held = hold(phone);
            List<PredictRules.Bet> next = new ArrayList<>();
            boolean found = false;
            for (PredictRules.Bet row : held.bets()) {
                if (bet.id().equals(row.id()) && "pending".equals(row.status())) {
                    found = true;
                    continue;
                }
                next.add(row);
            }
            if (!found) {
                return;
            }
            MissionRules.State mission = MissionRules.change(held.mission(), "预测退回", stake, now);
            try {
                users.saveMissionAndPredict(phone, held.missionRaw(), encodeMission(mission), held.predictRaw(), encodeBets(next));
                return;
            } catch (ConditionalCheckFailedException ex) {
                if (attempt == 1) {
                    throw new IllegalArgumentException("请稍后再试");
                }
            }
        }
    }

    private void appendGlobal(PredictRules.Bet bet) {
        for (int attempt = 0; attempt < 5; attempt++) {
            S3JsonStore.Loaded loaded = objects.load(KEY);
            List<PredictRules.Bet> bets = decodeBets(loaded.body());
            for (PredictRules.Bet row : bets) {
                if (bet.id().equals(row.id())) {
                    return;
                }
            }
            List<PredictRules.Bet> next = new ArrayList<>();
            next.add(bet);
            next.addAll(bets);
            trim(next, 5000);
            try {
                objects.store(KEY, json.writeValueAsBytes(Map.of("bets", next)), loaded.etag());
                return;
            } catch (S3Exception ex) {
                if (ex.statusCode() != 412) {
                    throw ex;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("预测无法写入", ex);
            }
        }
        throw new IllegalStateException("预测正在被同时更新");
    }

    private void removeGlobal(String id) {
        rewriteGlobal(current -> {
            current.removeIf(row -> id.equals(row.id()));
            return true;
        });
    }

    private void project(String phone) {
        projectPhones(Set.of(phone));
    }

    private void projectPhones(Set<String> phones) {
        Map<String, PredictRules.Bet> fresh = new LinkedHashMap<>();
        for (String phone : phones) {
            for (PredictRules.Bet bet : decodeText(users.profileJson(phone, "predict"))) {
                fresh.put(bet.id(), bet);
            }
        }
        if (fresh.isEmpty()) {
            return;
        }
        rewriteGlobal(current -> {
            boolean changed = false;
            for (int i = 0; i < current.size(); i++) {
                PredictRules.Bet newer = fresh.get(current.get(i).id());
                if (newer != null && !newer.status().equals(current.get(i).status())) {
                    current.set(i, newer);
                    changed = true;
                }
            }
            return changed;
        });
    }

    private void rewriteGlobal(java.util.function.Predicate<List<PredictRules.Bet>> editor) {
        for (int attempt = 0; attempt < 5; attempt++) {
            S3JsonStore.Loaded loaded = objects.load(KEY);
            List<PredictRules.Bet> bets = decodeBets(loaded.body());
            if (!editor.test(bets)) {
                return;
            }
            try {
                objects.store(KEY, json.writeValueAsBytes(Map.of("bets", bets)), loaded.etag());
                return;
            } catch (S3Exception ex) {
                if (ex.statusCode() != 412) {
                    throw ex;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("预测无法写入", ex);
            }
        }
        throw new IllegalStateException("预测正在被同时更新");
    }

    private List<PredictRules.Bet> loadGlobal() {
        return decodeBets(objects.load(KEY).body());
    }

    private JsonNode get(String url) {
        try {
            return http.execute(URI.create(url), HttpMethod.GET, request -> {
                request.getHeaders().set(HttpHeaders.USER_AGENT, "Mozilla/5.0");
                request.getHeaders().set(HttpHeaders.REFERER, matchBase + "/");
                request.getHeaders().setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            }, response -> {
                byte[] body = response.getBody().readNBytes(1_000_001);
                if (!response.getStatusCode().is2xxSuccessful() || body.length == 0 || body.length > 1_000_000) {
                    return null;
                }
                return json.readTree(body);
            });
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Held hold(String phone) {
        String missionRaw = users.profileJson(phone, "mission");
        String predictRaw = users.profileJson(phone, "predict");
        MissionRules.State mission;
        if (missionRaw == null || missionRaw.isBlank()) {
            mission = MissionRules.fresh();
        } else {
            try {
                mission = json.readValue(missionRaw, MissionRules.State.class);
            } catch (Exception ex) {
                mission = MissionRules.fresh();
            }
        }
        mission = MissionRules.normalize(mission, LocalDate.now(MissionRules.ZONE));
        return new Held(missionRaw == null ? "" : missionRaw, mission, predictRaw == null ? "" : predictRaw, decodeText(predictRaw));
    }

    private List<PredictRules.Bet> decodeText(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return decodeBets(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (RuntimeException ex) {
            return new ArrayList<>();
        }
    }

    private List<PredictRules.Bet> decodeBets(byte[] body) {
        if (body == null || body.length == 0) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = json.readTree(body).path("bets");
            if (!node.isArray()) {
                return new ArrayList<>();
            }
            List<PredictRules.Bet> bets = new ArrayList<>();
            for (JsonNode item : node) {
                bets.add(json.convertValue(item, PredictRules.Bet.class));
            }
            return bets;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String encodeMission(MissionRules.State state) {
        try {
            return json.writeValueAsString(state);
        } catch (Exception ex) {
            throw new IllegalArgumentException("投注失败");
        }
    }

    private String encodeBets(List<PredictRules.Bet> bets) {
        try {
            return json.writeValueAsString(Map.of("bets", bets));
        } catch (Exception ex) {
            throw new IllegalArgumentException("投注失败");
        }
    }

    private static void trim(List<PredictRules.Bet> bets, int limit) {
        while (bets.size() > limit) {
            int index = -1;
            for (int i = bets.size() - 1; i >= 0; i--) {
                if (!"pending".equals(bets.get(i).status())) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                bets.remove(bets.size() - 1);
            } else {
                bets.remove(index);
            }
        }
    }

    private static PredictRules.Bet finish(PredictRules.Bet bet, PredictRules.Outcome outcome, String settledAt) {
        return new PredictRules.Bet(bet.id(), bet.userId(), bet.phone(), bet.nick(), bet.mid(), bet.type(), bet.market(),
                bet.betvalue(), bet.label(), bet.line(), bet.odds(), bet.stake(), outcome.status(), outcome.returned(),
                outcome.profit(), bet.placedAt(), settledAt, bet.clientId());
    }

    private static String payoutTitle(String status) {
        if ("push".equals(status)) {
            return "预测走水";
        }
        if ("half-lose".equals(status)) {
            return "预测退半";
        }
        if ("refund".equals(status)) {
            return "预测退回";
        }
        return "预测中奖";
    }

    private static Map<String, Object> placed(PredictRules.Bet bet, int balance) {
        Map<String, Object> data = viewBet(bet);
        data.put("balance", balance);
        return data;
    }

    private static Map<String, Object> viewBet(PredictRules.Bet bet) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", bet.id());
        row.put("mid", bet.mid());
        row.put("type", bet.type());
        row.put("market", bet.market());
        row.put("betvalue", bet.betvalue());
        row.put("label", bet.label());
        row.put("line", bet.line());
        row.put("odds", bet.odds());
        row.put("stake", bet.stake());
        row.put("status", bet.status());
        row.put("statusName", statusName(bet.status()));
        row.put("returned", bet.returned());
        row.put("profit", bet.profit());
        row.put("placedAt", bet.placedAt());
        row.put("settledAt", bet.settledAt() == null ? "" : bet.settledAt());
        return row;
    }

    private static Map<String, Object> viewRank(PredictRules.RankRow row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rank", row.rank());
        data.put("userId", row.userId());
        data.put("nick", row.nick());
        data.put("profit", row.profit());
        data.put("win", row.win());
        data.put("total", row.total());
        return data;
    }

    private static String statusName(String status) {
        return switch (status) {
            case "win" -> "赢";
            case "lose" -> "输";
            case "push" -> "走水";
            case "half-win" -> "赢半";
            case "half-lose" -> "输半";
            case "refund" -> "退回";
            default -> "未开奖";
        };
    }

    private record Held(String missionRaw, MissionRules.State mission, String predictRaw, List<PredictRules.Bet> bets) {
    }
}
