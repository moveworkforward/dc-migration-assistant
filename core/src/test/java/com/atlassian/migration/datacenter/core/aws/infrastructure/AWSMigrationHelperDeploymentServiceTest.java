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

package com.atlassian.migration.datacenter.core.aws.infrastructure;

import com.atlassian.migration.datacenter.core.aws.CfnApi;
import com.atlassian.migration.datacenter.dto.MigrationContext;
import com.atlassian.migration.datacenter.spi.MigrationService;
import com.atlassian.migration.datacenter.spi.MigrationStage;
import com.atlassian.migration.datacenter.spi.exceptions.InvalidMigrationStageError;
import com.atlassian.migration.datacenter.spi.infrastructure.InfrastructureDeploymentError;
import com.atlassian.migration.datacenter.spi.infrastructure.InfrastructureDeploymentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse;
import software.amazon.awssdk.services.autoscaling.model.Instance;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.atlassian.migration.datacenter.core.aws.infrastructure.AWSMigrationHelperDeploymentService.STACK_RESOURCE_DEAD_LETTER_QUEUE_NAME;
import static com.atlassian.migration.datacenter.core.aws.infrastructure.AWSMigrationHelperDeploymentService.STACK_RESOURCE_QUEUE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AWSMigrationHelperDeploymentServiceTest {

    static final String APPLICATION_DEPLOYMENT = "application-deployment";
    static final String DEPLOYMENT_ID = APPLICATION_DEPLOYMENT + "-migration";
    static final String FS_DOWNLOAD_DOC = "fs-download-doc";
    static final String FS_DOWNLOAD_STATUS_DOC = "fs-download-status-doc";
    static final String DB_RESTORE_DOC = "db-restore-doc";
    static final String MIGRATION_ASG = "migration-asg";
    static final String MIGRATION_BUCKET = "migration-bucket";
    static final String MIGRATION_HOST_INSTANCE_ID = "i-12345";
    public static final String QUEUE_PHYSICAL_RESOURCE_ID = "https://sqs.someRegion.amazonaws.com/accountId/queue-name";
    public static final String DEAD_LETTER_QUEUE_PHYSICAL_RESOURCE_ID = "https://sqs.someRegion.amazonaws.com/accountId/dead-letter-queue-name";

    @Mock
    CfnApi mockCfn;

    @Mock
    MigrationService mockMigrationService;

    AWSMigrationHelperDeploymentService sut;

    @Mock
    AutoScalingClient mockAutoscaling;

    @Mock
    MigrationContext mockContext;

    private AtomicReference<String> deploymentId;

    @BeforeEach
    void setUp() {
        lenient().when(mockMigrationService.getCurrentContext()).thenReturn(mockContext);

        deploymentId = new AtomicReference<>();

        lenient().when(mockContext.getHelperStackDeploymentId()).thenReturn(deploymentId.get());

        sut = new AWSMigrationHelperDeploymentService(mockCfn, () -> mockAutoscaling, mockMigrationService, 1);
    }

    @Test
    void shouldNameMigrationStackAfterApplicationStackWithSuffixAndStoreInContext() throws InfrastructureDeploymentError {
        when(mockContext.getApplicationDeploymentId()).thenReturn(APPLICATION_DEPLOYMENT);
        doAnswer(invocation -> {
            deploymentId.set(invocation.getArgument(0));
            return null;
        }).when(mockContext).setHelperStackDeploymentId(anyString());
        givenMigrationStackHasStartedDeploying();

        assertEquals(DEPLOYMENT_ID, deploymentId.get());
    }

    @Test
    void shouldProvisionCloudFormationStack() throws InfrastructureDeploymentError {
        when(mockContext.getApplicationDeploymentId()).thenReturn(APPLICATION_DEPLOYMENT);
        givenMigrationStackHasStartedDeploying();

        verify(mockCfn).provisionStack("https://trebuchet-public-resources.s3.amazonaws.com/migration-helper.yml", DEPLOYMENT_ID, Collections.emptyMap());
    }

    @Test
    void shouldReturnInProgressWhileCloudformationDeploymentIsOngoing() {
        when(mockMigrationService.getCurrentStage()).thenReturn(MigrationStage.PROVISION_MIGRATION_STACK_WAIT);
        when(mockContext.getDeploymentState()).thenReturn(InfrastructureDeploymentState.CREATE_IN_PROGRESS);

        assertEquals(InfrastructureDeploymentState.CREATE_IN_PROGRESS, sut.getDeploymentStatus());
    }

    @Test
    void shouldReturnCompleteWhenCloudFormationDeploymentSucceeds() throws InterruptedException, InfrastructureDeploymentError {
        when(mockMigrationService.getCurrentStage()).thenReturn(MigrationStage.PROVISION_MIGRATION_STACK);
        when(mockContext.getDeploymentState()).thenReturn(InfrastructureDeploymentState.CREATE_COMPLETE);

        Thread.sleep(100);

        assertEquals(InfrastructureDeploymentState.CREATE_COMPLETE, sut.getDeploymentStatus());
    }

    @Test
    void shouldReturnErrorWhenCloudFormationDeploymentFails() throws InterruptedException, InfrastructureDeploymentError {
        when(mockMigrationService.getCurrentStage()).thenReturn(MigrationStage.PROVISIONING_ERROR);
        when(mockContext.getDeploymentState()).thenReturn(InfrastructureDeploymentState.CREATE_FAILED);

        InfrastructureDeploymentState state = sut.getDeploymentStatus();
        assertEquals(InfrastructureDeploymentState.CREATE_FAILED, state);
    }

    @Test
    void shouldGatherResourcesRequiredForMigration() throws InterruptedException, InfrastructureDeploymentError {
        givenMigrationStackDeploymentWillCompleteSuccessfully();
        givenMigrationStackHasStartedDeploying();

        Thread.sleep(1100);

        assertStackOutputsAreAvailable();
    }

    @Test
    void shouldThrowErrorWhenAtLeastOneStackOutputHasNotBeenPersisted() throws InterruptedException, InfrastructureDeploymentError {
        givenMigrationStackDeploymentWillFail();
        givenMigrationStackHasStartedDeploying();

        Thread.sleep(100);

        assertGettingStackOutputsThrowsError();
    }

    @Test
    void shouldReturnNotInProgressWhenNotInMigrationStackDeploymentPhase() {
        when(mockMigrationService.getCurrentStage()).thenReturn(MigrationStage.PROVISION_APPLICATION_WAIT);

        assertEquals(InfrastructureDeploymentState.NOT_DEPLOYING, sut.getDeploymentStatus());
    }

    @Test
    void shouldReturnDeploymentCompleteWhenAfterApplicationDeploymentAndNoError() {
        when(mockMigrationService.getCurrentStage()).thenReturn(MigrationStage.FS_MIGRATION_COPY);
        assertEquals(InfrastructureDeploymentState.CREATE_COMPLETE, sut.getDeploymentStatus());
    }

    @Test
    void shouldReturnDeploymentFailedWhenProvisioningError() {
        when(mockMigrationService.getCurrentStage()).thenReturn(MigrationStage.PROVISIONING_ERROR);
        when(mockContext.getDeploymentState()).thenReturn(InfrastructureDeploymentState.CREATE_FAILED);
        assertEquals(InfrastructureDeploymentState.CREATE_FAILED, sut.getDeploymentStatus());
    }

    private void assertGettingStackOutputsThrowsError() {
        ArrayList<Executable> outputGetters = new ArrayList<>();
        outputGetters.add(sut::getMigrationS3BucketName);
        outputGetters.add(sut::getDbRestoreDocument);
        outputGetters.add(sut::getFsRestoreDocument);
        outputGetters.add(sut::getFsRestoreStatusDocument);
        outputGetters.add(sut::getMigrationHostInstanceId);
        outputGetters.add(sut::getDeadLetterQueueResource);
        outputGetters.add(sut::getQueueResource);

        outputGetters.forEach(outputGetter -> assertThrows(InfrastructureDeploymentError.class, outputGetter));
    }

    private void assertStackOutputsAreAvailable() throws InfrastructureDeploymentError {
        assertEquals(FS_DOWNLOAD_DOC, sut.getFsRestoreDocument());
        assertEquals(FS_DOWNLOAD_STATUS_DOC, sut.getFsRestoreStatusDocument());
        assertEquals(DB_RESTORE_DOC, sut.getDbRestoreDocument());
        assertEquals(MIGRATION_BUCKET, sut.getMigrationS3BucketName());
        assertEquals(MIGRATION_HOST_INSTANCE_ID, sut.getMigrationHostInstanceId());
        assertEquals(QUEUE_PHYSICAL_RESOURCE_ID, sut.getQueueResource());
        assertEquals(DEAD_LETTER_QUEUE_PHYSICAL_RESOURCE_ID, sut.getDeadLetterQueueResource());
    }

    private void givenMigrationStackHasStartedDeploying() throws InfrastructureDeploymentError {
        when(mockMigrationService.getCurrentContext()).thenReturn(mockContext);
        try {
            sut.deployMigrationInfrastructure(Collections.emptyMap());
        } catch (InvalidMigrationStageError invalidMigrationStageError) {
            fail("invalid migration stage error thrown while deploying migration helper", invalidMigrationStageError);
        }
    }

    private void givenMigrationStackDeploymentWillCompleteSuccessfully() {
        givenContextContainsMigrationHelperStackId();
        when(mockCfn.getStatus(DEPLOYMENT_ID)).thenReturn(InfrastructureDeploymentState.CREATE_COMPLETE);

        Stack completedStack = Stack.builder()
                .outputs(Output.builder().outputKey("DownloadSSMDocument").outputValue(FS_DOWNLOAD_DOC).build(),
                        Output.builder().outputKey("DownloadStatusSSMDocument").outputValue(FS_DOWNLOAD_STATUS_DOC).build(),
                        Output.builder().outputKey("RdsRestoreSSMDocument").outputValue(DB_RESTORE_DOC).build(),
                        Output.builder().outputKey("ServerGroup").outputValue(MIGRATION_ASG).build(),
                        Output.builder().outputKey("MigrationBucket").outputValue(MIGRATION_BUCKET).build()
                ).build();

        when(mockCfn.getStack(DEPLOYMENT_ID)).thenReturn(Optional.of(completedStack));
        lenient().when(mockAutoscaling.describeAutoScalingGroups(
                DescribeAutoScalingGroupsRequest.builder()
                        .autoScalingGroupNames(MIGRATION_ASG)
                        .build()
        )).thenReturn(
                DescribeAutoScalingGroupsResponse.builder()
                        .autoScalingGroups(
                                AutoScalingGroup.builder()
                                        .instances(
                                                Instance.builder()
                                                        .instanceId(MIGRATION_HOST_INSTANCE_ID).build()
                                        ).build()
                        ).build()
        );

        when(mockCfn.getStackResources(DEPLOYMENT_ID)).thenReturn(new HashMap<String, StackResource>() {{
            put(STACK_RESOURCE_QUEUE_NAME, StackResource.builder().physicalResourceId(QUEUE_PHYSICAL_RESOURCE_ID).build());
            put(STACK_RESOURCE_DEAD_LETTER_QUEUE_NAME, StackResource.builder().physicalResourceId(DEAD_LETTER_QUEUE_PHYSICAL_RESOURCE_ID).build());
        }});

        expectStackDetailsToBePersistedInMigrationContext();
    }

    //Migration context stub object/data-class may make this test simpler
    private void expectStackDetailsToBePersistedInMigrationContext() {
        doNothing().when(mockContext).setFsRestoreSsmDocument(FS_DOWNLOAD_DOC);
        lenient().when(mockContext.getFsRestoreSsmDocument()).thenReturn(FS_DOWNLOAD_DOC);

        doNothing().when(mockContext).setFsRestoreStatusSsmDocument(FS_DOWNLOAD_STATUS_DOC);
        lenient().when(mockContext.getFsRestoreStatusSsmDocument()).thenReturn(FS_DOWNLOAD_STATUS_DOC);

        doNothing().when(mockContext).setRdsRestoreSsmDocument(DB_RESTORE_DOC);
        lenient().when(mockContext.getRdsRestoreSsmDocument()).thenReturn(DB_RESTORE_DOC);

        doNothing().when(mockContext).setMigrationStackAsgIdentifier(MIGRATION_ASG);
        lenient().when(mockContext.getMigrationStackAsgIdentifier()).thenReturn(MIGRATION_ASG);

        doNothing().when(mockContext).setMigrationBucketName(MIGRATION_BUCKET);
        lenient().when(mockContext.getMigrationBucketName()).thenReturn(MIGRATION_BUCKET);

        doNothing().when(mockContext).setMigrationQueueUrl(QUEUE_PHYSICAL_RESOURCE_ID);
        lenient().when(mockContext.getMigrationQueueUrl()).thenReturn(QUEUE_PHYSICAL_RESOURCE_ID);

        doNothing().when(mockContext).setMigrationDLQueueUrl(DEAD_LETTER_QUEUE_PHYSICAL_RESOURCE_ID);
        lenient().when(mockContext.getMigrationDLQueueUrl()).thenReturn(DEAD_LETTER_QUEUE_PHYSICAL_RESOURCE_ID);

        lenient().doNothing().when(mockContext).save();
    }

    private void givenMigrationStackDeploymentWillFail() {
        givenContextContainsMigrationHelperStackId();
        when(mockCfn.getStatus(DEPLOYMENT_ID)).thenReturn(InfrastructureDeploymentState.CREATE_FAILED);
    }

    private void givenContextContainsMigrationHelperStackId() {
        lenient().when(mockContext.getHelperStackDeploymentId()).thenReturn(DEPLOYMENT_ID);
    }
}