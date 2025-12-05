package com.example.bookkeeperai.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SttService {

    private final HuggingFaceClient hfClient;

    public String transcribe(byte[] audioBytes) throws Exception {
        return hfClient.speechToText(audioBytes);
    }
}

