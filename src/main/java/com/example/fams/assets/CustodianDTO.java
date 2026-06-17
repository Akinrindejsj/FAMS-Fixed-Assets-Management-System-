package com.example.fams.assets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for custodian representation in asset forms
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustodianDTO {
    private String id;
    private String name;
    private String username;
}

