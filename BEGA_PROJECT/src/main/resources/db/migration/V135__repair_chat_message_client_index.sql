DECLARE
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955); -- ORA-00955
BEGIN
    BEGIN
        EXECUTE IMMEDIATE q'[
            CREATE UNIQUE INDEX uk_chat_messages_party_sender_client_msg
            ON chat_messages (
                CASE WHEN client_message_id IS NOT NULL THEN party_id END,
                CASE WHEN client_message_id IS NOT NULL THEN sender_id END,
                CASE WHEN client_message_id IS NOT NULL THEN client_message_id END
            )
        ]';
    EXCEPTION
        WHEN e_index_exists THEN NULL;
    END;
END;
/
