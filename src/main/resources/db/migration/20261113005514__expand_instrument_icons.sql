-- Adds bowed strings, harp, winds, brass and bellows icons to the instrument catalog.
ALTER TABLE instruments DROP CONSTRAINT instruments_icon_check;
ALTER TABLE instruments ADD CONSTRAINT instruments_icon_check CHECK (icon IN (
    'MUSIC', 'PIANO', 'KEYBOARD', 'GUITAR', 'PERCUSSION', 'VOICE', 'VIOLIN', 'CELLO', 'HARP',
    'FLUTE', 'CLARINET', 'SAXOPHONE', 'TRUMPET', 'BANDONEON'));
