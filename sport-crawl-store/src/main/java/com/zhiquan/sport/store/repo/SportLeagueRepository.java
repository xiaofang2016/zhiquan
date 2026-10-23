package com.zhiquan.sport.store.repo;

import com.zhiquan.sport.store.MatchKeys;
import com.zhiquan.sport.store.aws.Attrs;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.entity.SportLeague;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SportLeagueRepository {

    private final DynamoDbClient dynamo;
    private final SportAwsProperties aws;

    public SportLeagueRepository(DynamoDbClient dynamo, SportAwsProperties aws) {
        this.dynamo = dynamo;
        this.aws = aws;
    }

    public void upsert(SportLeague row) {
        int sourceId = row.getSourceId();
        int sportType = row.getSportType();
        int sortOrder = row.getSortOrder() == null ? 0 : row.getSortOrder();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", Attrs.s("LEAGUE"));
        item.put("sk", Attrs.s(MatchKeys.leagueSk(sourceId, sportType)));
        item.put("source_id", Attrs.n(sourceId));
        item.put("sport_type", Attrs.n(sportType));
        item.put("sort_order", Attrs.n(sortOrder));
        item.put("payload", Attrs.s(row.getPayload()));
        item.put("updated_at", Attrs.s(Attrs.now()));
        dynamo.putItem(PutItemRequest.builder().tableName(aws.getLeagueTable()).item(item).build());
    }

    public List<SportLeague> listAll() {
        List<SportLeague> rows = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        do {
            QueryRequest.Builder request = QueryRequest.builder()
                    .tableName(aws.getLeagueTable())
                    .keyConditionExpression("#pk = :pk")
                    .expressionAttributeNames(Map.of("#pk", "pk"))
                    .expressionAttributeValues(Map.of(":pk", Attrs.s("LEAGUE")));
            if (start != null) {
                request.exclusiveStartKey(start);
            }
            QueryResponse response = dynamo.query(request.build());
            for (Map<String, AttributeValue> item : response.items()) {
                SportLeague row = new SportLeague();
                row.setSourceId(Attrs.num(item, "source_id"));
                row.setSportType(Attrs.num(item, "sport_type"));
                row.setSortOrder(Attrs.num(item, "sort_order"));
                row.setPayload(Attrs.str(item, "payload"));
                row.setUpdatedAt(Attrs.time(Attrs.str(item, "updated_at")));
                rows.add(row);
            }
            start = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (start != null);
        rows.sort(Comparator.comparing((SportLeague row) -> row.getSortOrder() == null ? 0 : row.getSortOrder())
                .thenComparing(row -> row.getSourceId() == null ? 0 : row.getSourceId()));
        return rows;
    }
}
