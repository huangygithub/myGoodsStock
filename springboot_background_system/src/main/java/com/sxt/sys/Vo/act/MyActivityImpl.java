package com.sxt.sys.Vo.act;

import java.util.List;

import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.pvm.process.ProcessDefinitionImpl;

public class MyActivityImpl extends ActivityImpl {

	public MyActivityImpl(String id, ProcessDefinitionImpl processDefinition) {
		super(id, processDefinition);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public List<PvmTransition> getOutgoingTransitions() {
		// TODO Auto-generated method stub
		return super.getOutgoingTransitions();
	}

}
