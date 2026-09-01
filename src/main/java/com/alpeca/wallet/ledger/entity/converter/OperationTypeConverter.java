package com.alpeca.wallet.ledger.entity.converter;

import com.alpeca.wallet.ledger.entity.OperationType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts OperationType values to and from operation_type database enum labels.
 */
@Converter(autoApply = true)
class OperationTypeConverter implements AttributeConverter<OperationType, String> {

	@Override
	public String convertToDatabaseColumn(OperationType attribute) {
		return attribute == null ? null : attribute.getValue();
	}

	@Override
	public OperationType convertToEntityAttribute(String dbData) {
		return dbData == null ? null : OperationType.fromValue(dbData);
	}
}
