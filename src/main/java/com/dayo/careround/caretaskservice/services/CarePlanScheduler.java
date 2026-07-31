package com.dayo.careround.caretaskservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CarePlanScheduler {

    private final CarePlanService carePlanService;

    @Scheduled(fixedDelay = 60000)
    public void sweep() {
        carePlanService.generateDueTasks();
    }
}