package com.alpeca.wallet.ledger.entity.converter;

import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts WalletUpdatedOutboxStatus values to and from wallet_updated_outbox_status database enum labels.
 */
@Converter(autoApply = true)
class WalletUpdatedOutboxStatusConverter implements AttributeConverter<WalletUpdatedOutboxStatus, String> {

	@Override
	public String convertToDatabaseColumn(WalletUpdatedOutboxStatus attribute) {
		return attribute == null ? null : attribute.getValue();
	}

	@Override
	public WalletUpdatedOutboxStatus convertToEntityAttribute(String dbData) {
		return dbData == null ? null : WalletUpdatedOutboxStatus.fromValue(dbData);
	}
}
