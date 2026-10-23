-- How urgent a task is: HIGH, MEDIUM or LOW. Existing tasks become MEDIUM through the default.
ALTER TABLE TASK ADD COLUMN PRIORITY VARCHAR(10) DEFAULT 'MEDIUM' NOT NULL;
