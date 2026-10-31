package com.dayo.careround.caretaskservice.grpc;

import com.dayo.careround.caretaskservice.exception.DownstreamServiceUnavailableException;
import com.dayo.careround.caretaskservice.exception.PatientNotFoundException;
import com.dayo.careround.patientservice.grpc.PatientCensusServiceGrpc;
import com.dayo.careround.patientservice.grpc.PatientExistsRequest;
import com.dayo.careround.patientservice.grpc.PatientExistsResponse;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PatientLookupClient {

    @GrpcClient("patient-service")
    private PatientCensusServiceGrpc.PatientCensusServiceBlockingStub patientStub;

    @Value("${app.grpc.deadline-ms:5000}")
    private long deadlineMs;

    // The patient's ward (publicId). Unknown patient -> PatientNotFoundException (404); patient-service unreachable
    // -> DownstreamServiceUnavailableException (503, fail closed: a task must not be created for an unverified patient).
    public UUID wardOf(UUID patientId) {
        PatientExistsResponse reply;
        try {
            reply = patientStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .patientExists(PatientExistsRequest.newBuilder().setPatientId(patientId.toString()).build());
        } catch (StatusRuntimeException e) {
            throw new DownstreamServiceUnavailableException("Could not verify patient (patient-service: " + e.getStatus().getCode() + ")", e);
        }
        if (!reply.getExists()) {
            throw new PatientNotFoundException("No patient with that id");
        }
        return reply.getWardId().isEmpty() ? null : UUID.fromString(reply.getWardId());
    }
}
