-- Fix transaction_type constraint to match the TransactionType enum in code
-- The enum has: INIT, ALLOCATION, REWARD, DEDUCT, REFUND
-- The original constraint had: INIT, DEDUCT, REFUND, AWARD

-- Drop the old constraint
ALTER TABLE budget_transactions DROP CONSTRAINT IF EXISTS chk_transaction_type;

-- Add the new constraint with correct values
ALTER TABLE budget_transactions ADD CONSTRAINT chk_transaction_type
    CHECK (transaction_type IN ('INIT', 'ALLOCATION', 'REWARD', 'DEDUCT', 'REFUND'));