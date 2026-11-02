"""页面连上后接收全部群。新消息推给正在看的连接，新群也推过去。"""

import json
import os

TABLE = os.environ.get("TABLE", "zhiquan_chat")
WS_ENDPOINT = os.environ.get("WS_ENDPOINT", "")
REGION = os.environ.get("AWS_REGION", "ap-northeast-1")

_table = None
_mgmt = None


def handler(event, context):
    if event.get("requestContext"):
        return socket_event(event)
    handled = False
    groups = event.get("groups")
    if isinstance(groups, list):
        handled = True
        for group in groups:
            row = live_group(group)
            if row:
                push({"group": row})
    items = event.get("items")
    if isinstance(items, list):
        handled = True
        for item in items:
            if isinstance(item, dict):
                payload = live_payload(item)
                if payload:
                    push(payload)
    if handled:
        return {"ok": True}
    for record in event.get("Records") or []:
        deliver(record)
    return {"ok": True}


def socket_event(event):
    route = event["requestContext"].get("routeKey")
    connection_id = event["requestContext"]["connectionId"]
    if route == "$connect":
        table().put_item(Item={"pk": "live", "sk": conn_key(connection_id), "groupId": "0"})
        return {"statusCode": 200}
    if route == "$disconnect":
        drop_connection(connection_id)
        return {"statusCode": 200}
    body = parse_body(event.get("body"))
    action = str(body.get("action") or "")
    if action == "ping":
        return {"statusCode": 200}
    if action != "subscribe":
        return {"statusCode": 400}
    move_connection(connection_id, target_of(body.get("groupId")))
    return {"statusCode": 200}


def deliver(record):
    if record.get("eventName") != "INSERT":
        return
    image = (record.get("dynamodb") or {}).get("NewImage") or {}
    payload = live_payload(unmarshall(image))
    if payload is None:
        return
    push(payload)


def live_payload(item):
    if str(item.get("kind") or "") != "group_msg":
        return None
    group_id = group_of(item)
    content = shown_content(str(item.get("content") or ""))
    if not group_id or not content.strip() or is_claim(content):
        return None
    sk = str(item.get("sk") or "")
    if not sk:
        return None
    if len(content) > 2000:
        content = content[:2000]
    return {
        "groupId": int(group_id),
        "message": {
            "time": millis(item.get("time") if item.get("time") not in (None, "") else item.get("sendTime")),
            "nick": str(item.get("nickName") or item.get("sendNickName") or ""),
            "sendUid": str(item.get("sendUid") or item.get("sendId") or ""),
            "type": msg_type(item),
            "content": content,
            "id": sk,
        },
    }


def shown_content(content):
    """@ 的正文在 origin_text。红包和图片仍推原始 JSON。"""
    text = str(content or "").strip()
    if not text.startswith("{") or "walletId" in text or '"files"' in text:
        return text
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return text
    if not isinstance(data, dict) or "walletId" in data or "files" in data:
        return text
    origin = data.get("origin_text")
    if isinstance(origin, str) and origin.strip():
        return origin.strip()
    return text


def is_claim(content):
    text = str(content or "").strip()
    if not text or "人已领取" in text:
        return False
    return text in ("领取了红包", "已领取了红包") or text.endswith("已领取了红包")


def group_of(item):
    found = group_number(item.get("gid"))
    if found:
        return found
    parts = str(item.get("sk") or "").split("#")
    if len(parts) >= 3 and parts[0] == "group" and parts[2] == "msg":
        return group_number(parts[1])
    return ""


def target_of(value):
    if str(value or "").strip() == "all":
        return "all"
    return group_number(value)


def fanout_keys(payload):
    if not isinstance(payload, dict):
        return []
    if payload.get("group") and "message" not in payload:
        return ["all"]
    group_id = payload.get("groupId")
    if group_id in (None, "", 0, "0"):
        return []
    text = str(group_id).strip()
    if not text.isdigit():
        return []
    return [str(int(text)), "all"]


def live_group(group):
    if not isinstance(group, dict):
        return None
    gid = group_number(group.get("id") if group.get("id") not in (None, "") else group.get("gid"))
    name = str(group.get("name") or "").strip()
    if not gid or not name:
        return None
    return {
        "id": int(gid),
        "gid": str(group.get("gid") or gid)[:32],
        "name": name[:80],
        "ownerName": str(group.get("ownerName") or "").strip()[:40],
        "notice": str(group.get("notice") or "")[:1024],
        "avatar": str(group.get("avatar") or "").strip()[:128],
    }


def group_number(value):
    text = str(value or "").strip()
    if not text.isdigit():
        return ""
    number = str(int(text))
    if number == "0" or len(number) > 18:
        return ""
    return number


def msg_type(item):
    raw = item.get("msgType")
    if raw in (None, ""):
        raw = item.get("type")
    if raw in (None, ""):
        return ""
    text = str(raw).strip()
    if "." in text:
        text = text.split(".", 1)[0]
    return text


def millis(raw):
    try:
        value = int(raw)
    except (TypeError, ValueError):
        return 0
    if value <= 0:
        return 0
    return value * 1000 if value < 10_000_000_000 else value


def unmarshall(image):
    return {key: attr_value(value) for key, value in (image or {}).items()}


def attr_value(value):
    if not isinstance(value, dict):
        return value
    if "S" in value:
        return value["S"]
    if "N" in value:
        return value["N"]
    if "NULL" in value:
        return None
    return ""


def parse_body(raw):
    if isinstance(raw, dict):
        return raw
    try:
        body = json.loads(raw or "{}")
    except (TypeError, json.JSONDecodeError):
        return {}
    return body if isinstance(body, dict) else {}


def conn_key(connection_id):
    return "conn#" + connection_id


def move_connection(connection_id, group_id):
    current = table().get_item(Key={"pk": "live", "sk": conn_key(connection_id)}).get("Item") or {}
    previous = str(current.get("groupId") or "")
    if previous not in ("", "0") and previous != group_id:
        table().delete_item(Key={"pk": "live#" + previous, "sk": conn_key(connection_id)})
    table().put_item(Item={"pk": "live", "sk": conn_key(connection_id), "groupId": group_id or "0"})
    if group_id:
        table().put_item(Item={
            "pk": "live#" + group_id,
            "sk": conn_key(connection_id),
            "connectionId": connection_id,
        })


def drop_connection(connection_id):
    current = table().get_item(Key={"pk": "live", "sk": conn_key(connection_id)}).get("Item") or {}
    previous = str(current.get("groupId") or "")
    table().delete_item(Key={"pk": "live", "sk": conn_key(connection_id)})
    if previous not in ("", "0"):
        table().delete_item(Key={"pk": "live#" + previous, "sk": conn_key(connection_id)})


def push(payload):
    if not WS_ENDPOINT:
        return
    keys = fanout_keys(payload)
    if not keys:
        return
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    sent = set()
    for key in keys:
        start = None
        while True:
            kwargs = {
                "KeyConditionExpression": "pk = :pk AND begins_with(sk, :sk)",
                "ExpressionAttributeValues": {":pk": "live#" + key, ":sk": "conn#"},
            }
            if start:
                kwargs["ExclusiveStartKey"] = start
            found = table().query(**kwargs)
            for item in found.get("Items") or []:
                connection_id = item.get("connectionId") or str(item.get("sk") or "")[5:]
                if not connection_id or connection_id in sent:
                    continue
                sent.add(connection_id)
                post(connection_id, data)
            start = found.get("LastEvaluatedKey")
            if not start:
                break


def post(connection_id, data):
    if not connection_id:
        return
    try:
        from botocore.exceptions import ClientError
        mgmt().post_to_connection(ConnectionId=connection_id, Data=data)
    except ClientError as error:
        code = error.response.get("Error", {}).get("Code", "")
        if code == "GoneException":
            drop_connection(connection_id)


def table():
    global _table
    if _table is None:
        import boto3
        _table = boto3.resource("dynamodb", region_name=REGION).Table(TABLE)
    return _table


def mgmt():
    global _mgmt
    if _mgmt is None:
        import boto3
        _mgmt = boto3.client("apigatewaymanagementapi", endpoint_url=WS_ENDPOINT, region_name=REGION)
    return _mgmt
