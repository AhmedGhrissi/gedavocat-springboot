-- Create document_shares table
CREATE TABLE IF NOT EXISTS document_shares (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    case_id VARCHAR(36) NOT NULL,
    target_role VARCHAR(20) NOT NULL,
    can_download TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_docshare_doc_role (document_id, target_role),
    INDEX idx_docshare_document (document_id),
    INDEX idx_docshare_case (case_id),
    INDEX idx_docshare_role (target_role),
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Add recipient_role column to case_share_links
ALTER TABLE case_share_links ADD COLUMN IF NOT EXISTS recipient_role VARCHAR(20) DEFAULT NULL;
