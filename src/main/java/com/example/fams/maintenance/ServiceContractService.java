package com.example.fams.maintenance;

import com.example.fams.assets.AssetService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ServiceContractService {

    private final ServiceContractRepository repository;
    private final AssetService assetService;

    public ServiceContractService(ServiceContractRepository repository, AssetService assetService) {
        this.repository = repository;
        this.assetService = assetService;
    }

    @Transactional(readOnly = true)
    public List<ServiceContract> findAll() {
        return repository.findAllByOrderByEndDateAsc();
    }

    @Transactional
    public ServiceContract create(Long assetId, String contractNumber, String vendor, String coverage,
                                  LocalDate startDate, LocalDate endDate, BigDecimal annualCost) {
        if (contractNumber == null || contractNumber.isBlank()) {
            throw new IllegalArgumentException("A contract number is required.");
        }
        if (vendor == null || vendor.isBlank()) {
            throw new IllegalArgumentException("A vendor is required.");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates are required.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("The contract end date cannot be before its start date.");
        }
        if (annualCost != null && annualCost.signum() < 0) {
            throw new IllegalArgumentException("The annual cost cannot be negative.");
        }
        ServiceContract contract = new ServiceContract();
        if (assetId != null) {
            contract.setAsset(assetService.findById(assetId));
        }
        contract.setContractNumber(contractNumber.trim());
        contract.setVendor(vendor.trim());
        contract.setCoverage(coverage == null || coverage.isBlank() ? null : coverage.trim());
        contract.setStartDate(startDate);
        contract.setEndDate(endDate);
        contract.setAnnualCost(annualCost);
        return repository.save(contract);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
