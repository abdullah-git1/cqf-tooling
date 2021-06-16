package org.opencds.cqf.tooling.operation;

import org.testng.annotations.Test;

import java.net.URISyntaxException;

import org.opencds.cqf.tooling.Operation;

public class GenerateCQLFromDroolOperationTest {
    @Test
    public void test_worked() throws URISyntaxException {
        String dataInputPath = "default.json";
        String operation = "GenerateCQLFromDrool";
        String inputFilePath = GenerateCQLFromDroolOperationTest.class.getResource(dataInputPath).toURI().getPath();
        String outputPath = "target/CqlFromDrool";
        String encoding = "json";
        String[] args = { "-" + operation, "-ifp=" + inputFilePath, "-op=" + outputPath, "-e=" + encoding };
        Operation generateCQLFromDroolOperation = new GenerateCQLFromDroolOperation();
        generateCQLFromDroolOperation.execute(args);
    }
}
