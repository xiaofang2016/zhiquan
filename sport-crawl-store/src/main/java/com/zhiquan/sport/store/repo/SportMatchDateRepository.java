package com.zhiquan.sport.store.repo;

import com.zhiquan.sport.store.aws.Attrs;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.entity.SportMatchDate;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SportMatchDateRepository {

    private final DynamoDbClient dynamo;
    private final SportAwsProperties aws;

    public SportMatchDateRepository(DynamoDbClient dynamo, SportAwsProperties aws) {
        this.dynamo = dynamo;
        this.aws = aws;
    }

    public void upsert(SportMatchDate row) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", Attrs.s("DATE"));
        item.put("sk", Attrs.s(row.getMatchDate().toString()));
        item.put("total_match", Attrs.n(row.getTotalMatch() == null ? 0 : row.getTotalMatch()));
        item.put("updated_at", Attrs.s(Attrs.now()));
        dynamo.putItem(PutItemRequest.builder().tableName(aws.getDateTable()).item(item).build());
    }

    public List<SportMatchDate> listAll() {
        List<SportMatchDate> rows = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        do {
            QueryRequest.Builder request = QueryRequest.builder()
                    .tableName(aws.getDateTable())
                    .keyConditionExpression("#pk = :pk")
                    .expressionAttributeNames(Map.of("#pk", "pk"))
                    .expressionAttributeValues(Map.of(":pk", Attrs.s("DATE")))
                    .scanIndexForward(true);
            if (start != null) {
                request.exclusiveStartKey(start);
            }
            QueryResponse response = dynamo.query(request.build());
            for (Map<String, AttributeValue> item : response.items()) {
                SportMatchDate row = new SportMatchDate();
                row.setMatchDate(LocalDate.parse(Attrs.str(item, "sk")));
                row.setTotalMatch(Attrs.num(item, "total_match"));
                row.setUpdatedAt(Attrs.time(Attrs.str(item, "updated_at")));
                rows.add(row);
            }
            start = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (start != null);
        return rows;
    }
}
