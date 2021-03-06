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
package org.eclipse.emf.ecoretools.ale.core.interpreter.services;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.acceleo.query.parser.AstBuilderListener;
import org.eclipse.acceleo.query.runtime.AcceleoQueryEvaluationException;
import org.eclipse.acceleo.query.runtime.IReadOnlyQueryEnvironment;
import org.eclipse.acceleo.query.runtime.IService;
import org.eclipse.acceleo.query.runtime.impl.EvaluationServices;
import org.eclipse.acceleo.query.runtime.impl.Nothing;
import org.eclipse.acceleo.query.validation.type.ClassType;
import org.eclipse.acceleo.query.validation.type.EClassifierType;
import org.eclipse.acceleo.query.validation.type.IType;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.DiagnosticChain;
import org.eclipse.emf.ecore.EObject;

/**
 * EvaluationServices able to notify before and after service calls
 */
public class NotifyingEvaluationServices extends EvaluationServices {

	private static final String INTERNAL_ERROR_MSG = "An internal error occured during evaluation of a query";
	
	List<ServiceCallListener> listeners;
	
	public NotifyingEvaluationServices(IReadOnlyQueryEnvironment queryEnv) {
		super(queryEnv);
		this.listeners = new ArrayList<ServiceCallListener>();
	}
	
	public NotifyingEvaluationServices(IReadOnlyQueryEnvironment queryEnv, List<ServiceCallListener> listeners) {
		super(queryEnv);
		this.listeners = listeners;
	}

	public Object call(String serviceName, Object[] arguments, Diagnostic diagnostic) {
		final Object result;

		if (arguments.length == 0) {
			throw new AcceleoQueryEvaluationException(
					"An internal error occured during evaluation of a query : at least one argument must be specified for service "
							+ serviceName + ".");
		}
		try {
			IType[] argumentTypes = getArgumentTypes(arguments);
			IService service = queryEnvironment.getLookupEngine().lookup(serviceName, argumentTypes);
			if (service == null) {
				Nothing placeHolder = nothing(SERVICE_NOT_FOUND, serviceSignature(serviceName, argumentTypes));
				addDiagnosticFor(diagnostic, Diagnostic.WARNING, placeHolder);
				result = placeHolder;
			} else {
				listeners.forEach(l -> l.preCall(service, arguments));
				result = callService(service, arguments, diagnostic);
				listeners.forEach(l -> l.postCall(service, arguments,result));
			}
			// CHECKSTYLE:OFF
		} catch (Exception e) {
			// CHECKSTYLE:ON
			throw new AcceleoQueryEvaluationException(INTERNAL_ERROR_MSG, e);
		}

		return result;
	}
	
	
	
	//FIXME: copy-pasted private methods below
	private Object callService(IService service, Object[] arguments, Diagnostic diagnostic) {
		try {
			final Object result = service.invoke(arguments);
			if (result instanceof Nothing) {
				addDiagnosticFor(diagnostic, Diagnostic.WARNING, (Nothing)result);
			}
			return result;
		} catch (AcceleoQueryEvaluationException e) {
			Nothing placeHolder = new Nothing(e.getMessage(), e);
			if (e.getCause() instanceof AcceleoQueryEvaluationException) {
				addDiagnosticFor(diagnostic, Diagnostic.WARNING, placeHolder);
			} else {
				addDiagnosticFor(diagnostic, Diagnostic.ERROR, placeHolder);
			}
			return placeHolder;
		}
	}
	
	private void addDiagnosticFor(Diagnostic chain, int severity, Nothing nothing) {
		if (chain instanceof DiagnosticChain) {
			Diagnostic child = new BasicDiagnostic(severity, AstBuilderListener.PLUGIN_ID, 0, nothing
					.getMessage(), new Object[] {nothing.getCause(), });
			((DiagnosticChain)chain).add(child);
		}
	}
	
	private IType[] getArgumentTypes(Object[] arguments) {
		IType[] argumentTypes = new IType[arguments.length];
		for (int i = 0; i < arguments.length; i++) {
			if (arguments[i] == null) {
				argumentTypes[i] = new ClassType(queryEnvironment, null);
			} else if (arguments[i] instanceof EObject) {
				argumentTypes[i] = new EClassifierType(queryEnvironment, ((EObject)arguments[i]).eClass());
			} else {
				argumentTypes[i] = new ClassType(queryEnvironment, arguments[i].getClass());
			}
		}
		return argumentTypes;
	}
	
	private Nothing nothing(String message, Object... msgArgs) {
		String formatedMessage = String.format(message, msgArgs);
		return new Nothing(formatedMessage);
	}
}
