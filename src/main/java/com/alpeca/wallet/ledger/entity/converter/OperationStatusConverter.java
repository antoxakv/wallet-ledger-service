package com.alpeca.wallet.ledger.entity.converter;

import com.alpeca.wallet.ledger.entity.OperationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts OperationStatus values to and from operation_status database enum labels.
 */
@Converter(autoApply = true)
class OperationStatusConverter implements AttributeConverter<OperationStatus, String> {

	@Override
	public String convertToDatabaseColumn(OperationStatus attribute) {
		return attribute == null ? null : attribute.getValue();
	}

	@Override
	public OperationStatus convertToEntityAttribute(String dbData) {
		return dbData == null ? null : OperationStatus.fromValue(dbData);
	}
}
