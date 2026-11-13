import unittest

from handler import apply_pairs, group_number, is_claim, live_payload, parse_pairs, rewrite_jx8, shown_content, unmarshall


def image(**fields):
    raw = {}
    for key, value in fields.items():
        if isinstance(value, int):
            raw[key] = {"N": str(value)}
        else:
            raw[key] = {"S": value}
    return unmarshall(raw)


class LivePayloadTest(unittest.TestCase):
    def test_group_message_matches_the_page_row(self):
        payload = live_payload(image(
            kind="group_msg",
            gid="0000008447",
            sk="group#0000008447#msg#0001793492389481#abcdefabcdefabcd",
            msgType=0,
            nickName="莉莉",
            sendUid="400275",
            content="你好[/发呆]",
            time=1793492389481,
        ))
        self.assertEqual(8447, payload["groupId"])
        self.assertEqual({
            "time": 1793492389481,
            "nick": "莉莉",
            "sendUid": "400275",
            "type": "0",
            "content": "你好[/发呆]",
            "id": "group#0000008447#msg#0001793492389481#abcdefabcdefabcd",
        }, payload["message"])

    def test_seconds_become_millis_and_gid_can_come_from_the_sort_key(self):
        payload = live_payload(image(
            kind="group_msg",
            sk="group#0000000390#msg#000000001700000000#abcdefabcdefabcd",
            msgType=10,
            content='{"walletId":"1","description":"西甲赞助商"}',
            time=1700000000,
        ))
        self.assertEqual(390, payload["groupId"])
        self.assertEqual("10", payload["message"]["type"])
        self.assertEqual(1700000000000, payload["message"]["time"])

    def test_claim_lines_and_other_kinds_stay_off_the_socket(self):
        claim = image(kind="group_msg", gid="8447", sk="group#8447#msg#1#aa", content="安亿十已领取了红包", msgType=90, time=2)
        summary = image(kind="group_msg", gid="8447", sk="group#8447#msg#1#bb", content="等12/200人已领取了红包", msgType=90, time=2)
        friend = image(kind="friend_msg", gid="8447", sk="friend#1#msg#1#aa", content="你好", msgType=0, time=2)
        self.assertIsNone(live_payload(claim))
        self.assertEqual("等12/200人已领取了红包", live_payload(summary)["message"]["content"])
        self.assertIsNone(live_payload(friend))
        self.assertTrue(is_claim("领取了红包"))
        self.assertEqual("", group_number("0"))
        self.assertEqual("8447", group_number("0000008447"))

    def test_mention_json_is_pushed_as_the_sentence(self):
        raw = '{"gid":"00000001042","origin_text":"@包包揣金豆爱你","select_obj":[{"nickname":"包包揣金豆","user_uid":"2310686"}]}'
        payload = live_payload(image(
            kind="group_msg", gid="1042", sk="group#00000001042#msg#1#aa", msgType=12, content=raw, time=3,
        ))
        self.assertEqual("@包包揣金豆爱你", payload["message"]["content"])
        self.assertEqual("12", payload["message"]["type"])
        self.assertEqual(shown_content('{"walletId":"1","description":"西甲赞助商"}'), '{"walletId":"1","description":"西甲赞助商"}')

    def test_text_type_zero_is_kept(self):
        payload = live_payload({
            "kind": "group_msg",
            "gid": "0000008447",
            "sk": "group#0000008447#msg#0000000000000099#livetestlivetest",
            "msgType": 0,
            "nickName": "通道",
            "sendUid": "0",
            "content": "实时通道已接通",
            "time": 1793531000000,
        })
        self.assertEqual("0", payload["message"]["type"])
        self.assertEqual(8447, payload["groupId"])

    def test_domain_ignores_case(self):
        self.assertEqual('{"name":"去 JX8.CC 和 JX8.CC"}', rewrite_jx8('{"name":"去 JX8.com 和 jx8.COM"}'))
        self.assertEqual("www.jx8.com", rewrite_jx8("www.jx8.com"))
        self.assertEqual("去 qiumishuo.cc", rewrite_jx8("去 qiumishuo.com"))
        self.assertEqual("www.qiumishuo.com", rewrite_jx8("www.qiumishuo.com"))

    def test_pairs_stay_one_to_one(self):
        rules = parse_pairs("www.jx8.com=www.JX8.CC\nJX8.com=JX8.CC\nJX8.com=JX8.NET\n")
        self.assertEqual([("www.jx8.com", "www.JX8.CC"), ("JX8.com", "JX8.NET")], rules)
        self.assertEqual("看 www.JX8.CC 和 JX8.NET", apply_pairs("看 www.jx8.com 和 jx8.com", rules))

    def test_messages_fan_out_to_the_group_and_everyone(self):
        from handler import fanout_keys, live_group, target_of
        self.assertEqual(["8447", "all"], fanout_keys({"groupId": 8447, "message": {"id": "a"}}))
        self.assertEqual(["all"], fanout_keys({"group": {"id": 9, "name": "新群"}}))
        self.assertEqual("all", target_of("all"))
        self.assertEqual("390", target_of("0000000390"))
        card = live_group({"id": 390, "gid": "0000000390", "name": "红包收米群", "avatar": "abc"})
        self.assertEqual(390, card["id"])
        self.assertEqual("红包收米群", card["name"])
        self.assertIsNone(live_group({"id": 1, "name": ""}))


if __name__ == "__main__":
    unittest.main()
