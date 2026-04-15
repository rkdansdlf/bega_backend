ALTER TABLE parties ADD cheering_side VARCHAR(16);

ALTER TABLE chat_messages ADD client_message_id VARCHAR(64);

CREATE UNIQUE INDEX uk_chat_messages_party_sender_client_msg
    ON chat_messages (party_id, sender_id, client_message_id);
