public isolated client class TypedescClient {

    private string serviceUrl;

    public isolated function init(string serviceUrl = "https://test.sh") returns error? {
        self.serviceUrl = serviceUrl;
    }

    // Test A: Process typedesc<string>
    remote isolated function processTypedescString(typedesc<string> expectedType) returns string {
        return "string";
    }

    // Test B: Process typedesc<Message>
    remote isolated function processTypedescRecord(typedesc<Message> expectedType) returns Message {
        return { body: "test" };
    }

    // Test C: Process typedesc<Message|MessageBatch>
    remote isolated function processTypedescUnion(typedesc<Message|MessageBatch> expectedType) returns Message|MessageBatch {
        return { body: "test" };
    }

    // Test D: Process typedesc<anydata> (should be skipped)
    remote isolated function processTypedescAnydata(typedesc<anydata> expectedType) returns anydata {
        return "test";
    }
}
