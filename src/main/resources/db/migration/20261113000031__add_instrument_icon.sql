-- Each instrument chooses its own visual icon; existing instruments start with the generic one.
ALTER TABLE instruments
    ADD COLUMN icon varchar(40) NOT NULL DEFAULT 'MUSIC',
    ADD CONSTRAINT instruments_icon_check
        CHECK (icon IN ('MUSIC', 'PIANO', 'KEYBOARD', 'GUITAR', 'PERCUSSION', 'VOICE'));
