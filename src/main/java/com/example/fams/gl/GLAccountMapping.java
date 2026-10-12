package com.example.fams.gl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Section 8 — assigns a real {@link GLAccount} to each {@link GLAccountType} role, for
 * one asset category. A row with {@code category == null} is the global default,
 * resolved when a category has no specific mapping of its own — same
 * asset-specific-over-category-default-over-global pattern used by
 * {@code DepreciationService.resolveParameters}.
 */
@Entity
@Table(name = "gl_account_mappings")
public class GLAccountMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null = global default mapping. */
    @Column(length = 120)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_cost_account_id")
    private GLAccount assetCostAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accumulated_depreciation_account_id")
    private GLAccount accumulatedDepreciationAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depreciation_expense_account_id")
    private GLAccount depreciationExpenseAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accumulated_impairment_account_id")
    private GLAccount accumulatedImpairmentAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "impairment_loss_account_id")
    private GLAccount impairmentLossAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revaluation_surplus_account_id")
    private GLAccount revaluationSurplusAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revaluation_loss_account_id")
    private GLAccount revaluationLossAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disposal_gain_account_id")
    private GLAccount disposalGainAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disposal_loss_account_id")
    private GLAccount disposalLossAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disposal_proceeds_account_id")
    private GLAccount disposalProceedsAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_payables_account_id")
    private GLAccount cashPayablesAccount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public GLAccount getAssetCostAccount() {
        return assetCostAccount;
    }

    public void setAssetCostAccount(GLAccount assetCostAccount) {
        this.assetCostAccount = assetCostAccount;
    }

    public GLAccount getAccumulatedDepreciationAccount() {
        return accumulatedDepreciationAccount;
    }

    public void setAccumulatedDepreciationAccount(GLAccount accumulatedDepreciationAccount) {
        this.accumulatedDepreciationAccount = accumulatedDepreciationAccount;
    }

    public GLAccount getDepreciationExpenseAccount() {
        return depreciationExpenseAccount;
    }

    public void setDepreciationExpenseAccount(GLAccount depreciationExpenseAccount) {
        this.depreciationExpenseAccount = depreciationExpenseAccount;
    }

    public GLAccount getAccumulatedImpairmentAccount() {
        return accumulatedImpairmentAccount;
    }

    public void setAccumulatedImpairmentAccount(GLAccount accumulatedImpairmentAccount) {
        this.accumulatedImpairmentAccount = accumulatedImpairmentAccount;
    }

    public GLAccount getImpairmentLossAccount() {
        return impairmentLossAccount;
    }

    public void setImpairmentLossAccount(GLAccount impairmentLossAccount) {
        this.impairmentLossAccount = impairmentLossAccount;
    }

    public GLAccount getRevaluationSurplusAccount() {
        return revaluationSurplusAccount;
    }

    public void setRevaluationSurplusAccount(GLAccount revaluationSurplusAccount) {
        this.revaluationSurplusAccount = revaluationSurplusAccount;
    }

    public GLAccount getRevaluationLossAccount() {
        return revaluationLossAccount;
    }

    public void setRevaluationLossAccount(GLAccount revaluationLossAccount) {
        this.revaluationLossAccount = revaluationLossAccount;
    }

    public GLAccount getDisposalGainAccount() {
        return disposalGainAccount;
    }

    public void setDisposalGainAccount(GLAccount disposalGainAccount) {
        this.disposalGainAccount = disposalGainAccount;
    }

    public GLAccount getDisposalLossAccount() {
        return disposalLossAccount;
    }

    public void setDisposalLossAccount(GLAccount disposalLossAccount) {
        this.disposalLossAccount = disposalLossAccount;
    }

    public GLAccount getDisposalProceedsAccount() {
        return disposalProceedsAccount;
    }

    public void setDisposalProceedsAccount(GLAccount disposalProceedsAccount) {
        this.disposalProceedsAccount = disposalProceedsAccount;
    }

    public GLAccount getCashPayablesAccount() {
        return cashPayablesAccount;
    }

    public void setCashPayablesAccount(GLAccount cashPayablesAccount) {
        this.cashPayablesAccount = cashPayablesAccount;
    }
}