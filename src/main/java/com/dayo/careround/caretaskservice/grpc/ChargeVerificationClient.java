package com.dayo.careround.caretaskservice.grpc;

import com.dayo.careround.caretaskservice.exception.DownstreamServiceUnavailableException;
import com.dayo.careround.wardservice.grpc.ChargeVerificationRequest;
import com.dayo.careround.wardservice.grpc.ChargeVerificationServiceGrpc;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ChargeVerificationClient {

    @GrpcClient("ward-service")
    private ChargeVerificationServiceGrpc.ChargeVerificationServiceBlockingStub chargeStub;

    @Value("${app.grpc.deadline-ms:5000}")
    private long deadlineMs;

    // Fails closed: ward-service unreachable -> 503, never "allowed".
    public boolean isActiveChargeNurse(UUID wardId, UUID nurseId) {
        try {
            return chargeStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                    .isActiveChargeNurse(ChargeVerificationRequest.newBuilder()
                            .setWardId(wardId.toString()).setNurseId(nurseId.toString()).build())
                    .getIsActiveChargeNurse();
        } catch (StatusRuntimeException e) {
            throw new DownstreamServiceUnavailableException("Could not verify charge nurse (ward-service: " + e.getStatus().getCode() + ")", e);
        }
    }
}
