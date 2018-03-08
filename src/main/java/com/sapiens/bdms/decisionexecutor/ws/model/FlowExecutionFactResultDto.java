package com.sapiens.bdms.decisionexecutor.ws.model;

import com.sapiens.bdms.java.exe.helper.base.FactType;
import com.sapiens.bdms.java.exe.helper.base.RowHit;

import java.util.List;
import java.util.Map;

public class FlowExecutionFactResultDto {
	private Object value;
	private Map<String, List<RowHit>> rowHits;

	public FlowExecutionFactResultDto(FactType factType){
		this.value = factType.getValue();
		this.rowHits = factType.getRowHits();
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	public Map<String, List<RowHit>> getRowHits() {
		return rowHits;
	}

	public void setRowHits(Map<String, List<RowHit>> rowHits) {
		this.rowHits = rowHits;
	}
}
