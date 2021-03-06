/*
 * Copyright 2020 Atlassian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.atlassian.migration.datacenter.core.aws.ssm;

import com.atlassian.migration.datacenter.core.aws.infrastructure.AWSMigrationHelperDeploymentService;
import com.atlassian.migration.datacenter.core.fs.download.s3sync.S3SyncFileSystemDownloader;
import com.atlassian.migration.datacenter.spi.infrastructure.InfrastructureDeploymentError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationRequest;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationResponse;
import software.amazon.awssdk.services.ssm.model.SendCommandRequest;
import software.amazon.awssdk.services.ssm.model.SendCommandResponse;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SSMApi {

    private static final Logger logger = LoggerFactory.getLogger(SSMApi.class);

    private Supplier<SsmClient> clientFactory;
    private final AWSMigrationHelperDeploymentService migrationHelperDeploymentService;

    private final String ssmS3KeyPrefix = "trebuchet-ssm-document-logs";

    public SSMApi(Supplier<SsmClient> clientFactory, AWSMigrationHelperDeploymentService migrationHelperDeploymentService) {
        this.clientFactory = clientFactory;
        this.migrationHelperDeploymentService = migrationHelperDeploymentService;
    }

    /**
     * Runs an SSM automatiom document against a specific EC2 instance with the specified parameters.
     *
     * @param documentName        The name of the document to run. The latest version will be used.
     * @param targetEc2InstanceId The instance ID of the EC2 instance to run this command on
     * @param commandParameters   The parameters for the command. The key should be the parameter name and the value should
     *                            be a list filled with the lines to be used as the parameter value. If the parameter-type
     *                            is a plain string then the list only have one element in it.
     * @return the command ID of the invoked command.
     */
    public String runSSMDocument(String documentName, String targetEc2InstanceId, Map<String, List<String>> commandParameters) throws S3SyncFileSystemDownloader.CannotLaunchCommandException {
        logger.debug("Running document {} against instance {} with parameters {}", documentName, targetEc2InstanceId, commandParameters.entrySet());
        SsmClient client = clientFactory.get();
        final String migrationS3BucketName;
        try {
            migrationS3BucketName = migrationHelperDeploymentService.getMigrationS3BucketName();
        } catch (InfrastructureDeploymentError infrastructureDeploymentError) {
            throw new S3SyncFileSystemDownloader.CannotLaunchCommandException("cannot get migration bucket for publishing SSM command logs", infrastructureDeploymentError);
        }
        SendCommandRequest request = SendCommandRequest.builder()
                .documentName(documentName)
                .documentVersion("$LATEST")
                .instanceIds(targetEc2InstanceId)
                .parameters(commandParameters)
                .timeoutSeconds(600)
                .comment("Command run by Jira DC Migration Assistant")
                .outputS3BucketName(migrationS3BucketName)
                .outputS3KeyPrefix(ssmS3KeyPrefix)
                .build();

        SendCommandResponse response = client.sendCommand(request);

        return response.command().commandId();
    }

    /**
     * Gets the invocation of the specified command on the specified EC2 instance. You will want
     * to check the details of the command yourself. Noteworthy response fields include {@link GetCommandInvocationResponse#status()},
     * {@link GetCommandInvocationResponse#standardOutputContent()}
     *
     * @param commandId           The id of the command from calling {@link SSMApi#runSSMDocument(String, String, Map)}
     * @param targetEc2InstanceId the EC2 instance the command is running on. Should be the same as the targetEc2InstanceId from calling {@link SSMApi#runSSMDocument(String, String, Map)}
     * @return The response from the AWS SDK v2
     * @throws software.amazon.awssdk.services.ssm.model.InvalidCommandIdException       (RuntimeException) - when the command ID is not a valid SSM Command ID
     * @throws software.amazon.awssdk.services.ssm.model.InvocationDoesNotExistException (RuntimeException) - when the command invocation (combination of command ID and instance ID) does not exist
     * @see SsmClient#getCommandInvocation(GetCommandInvocationRequest) for other exception details
     */
    public GetCommandInvocationResponse getSSMCommand(String commandId, String targetEc2InstanceId) {
        logger.debug("Getting status of command {} on instance {}", commandId, targetEc2InstanceId);

        SsmClient client = clientFactory.get();
        GetCommandInvocationRequest request = GetCommandInvocationRequest.builder()
                .commandId(commandId)
                .instanceId(targetEc2InstanceId)
                .build();

        GetCommandInvocationResponse response = client.getCommandInvocation(request);

        return response;
    }

    public String getSsmS3KeyPrefix() {
        return ssmS3KeyPrefix;
    }
}
