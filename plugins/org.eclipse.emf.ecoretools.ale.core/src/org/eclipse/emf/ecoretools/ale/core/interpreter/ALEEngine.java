/*******************************************************************************
 * Copyright (c) 2017 Inria and Obeo.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Inria - initial API and implementation
 *******************************************************************************/
package org.eclipse.emf.ecoretools.ale.core.interpreter;

import java.util.List;

import org.eclipse.acceleo.query.runtime.EvaluationResult;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecoretools.ale.implementation.Method;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;

public class ALEEngine {
	
	EvalEnvironment implemEnv;
	
	public ALEEngine (EvalEnvironment evalEnv) {
		this.implemEnv = evalEnv;
	}
	
	EvaluationResult res;
	
	public EvaluationResult eval(EObject target, Method mainOp, List<Object> args) {
		MethodEvaluator evaluator = new MethodEvaluator(new ExpressionEvaluationEngine(implemEnv.getQueryEnvironment(),implemEnv.getListeners()), implemEnv.getFeatureAccess());
		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(this.implemEnv.getResourceSet());
		if (domain == null) {
			domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain(this.implemEnv.getResourceSet());
		}
		domain.getCommandStack().execute(new RecordingCommand(domain) {
			@Override
			protected void doExecute() {
				try {
					res = evaluator.eval(target,mainOp,args);
				} catch(CriticalFailure e) {
					res = new EvaluationResult(null, e.diagnostics);
					implemEnv.logger.notify(e.diagnostics);
				}
			}
		});

		return res;
	}
	
	public EvalEnvironment getEvalEnvironment() {
		return implemEnv;
	}
}