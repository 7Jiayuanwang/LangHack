package com.english.dto;

import lombok.Data;

@Data
public class HotDeployRequest {
    private String branch;
    private Boolean skipBuild;
    private Boolean skipPull;
    private Boolean dryRun;
    private String remote;
}
