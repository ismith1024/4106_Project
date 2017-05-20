CREATE TABLE IF NOT EXISTS classifierResults(
	year INT NOT NULL,
	month INT NOT NULL,
	day INT NOT NULL,
	ticker TEXT NOT NULL,
	knownClass INT,
	nnFundamentals REAL,
	nnEconomics REAL,
	kmFundamentals REAL,
	kmEconomics REAL,
	PRIMARY KEY(year, month, day, ticker)
);