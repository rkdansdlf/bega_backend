CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_messages_party_sender_client_msg
    ON chat_messages (party_id, sender_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
