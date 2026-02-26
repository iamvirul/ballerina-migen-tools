public type Message record {|
    anydata body;
    string contentType = "BYTE_ARRAY";
    string messageId?;
    string to?;
    string replyTo?;
|};

public type MessageBatch record {|
    int messageCount = -1;
    Message[] messages = [];
|};
