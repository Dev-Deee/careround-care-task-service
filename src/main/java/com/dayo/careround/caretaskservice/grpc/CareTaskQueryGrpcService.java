package com.dayo.careround.caretaskservice.grpc;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import com.dayo.careround.caretaskservice.data.repositories.CareTaskRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class CareTaskQueryGrpcService extends CareTaskQueryServiceGrpc.CareTaskQueryServiceImplBase {

    private final CareTaskRepository careTaskRepository;

    @Override
    public void getPendingTaskIds(PendingTaskIdsRequest request, StreamObserver<PendingTaskIdsResponse> responseObserver) {
        UUID patientId = UUID.fromString(request.getPatientId());

        List<String> pendingTaskIds = careTaskRepository
                .findByPatientIdAndStatusIn(patientId, List.of(CareTaskStatus.PENDING, CareTaskStatus.IN_PROGRESS))
                .stream()
                .map(CareTask::getPublicId)
                .map(UUID::toString)
                .toList();

        PendingTaskIdsResponse response = PendingTaskIdsResponse.newBuilder()
                .addAllTaskIds(pendingTaskIds)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}