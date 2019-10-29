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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.acceleo.query.runtime.AcceleoQueryEvaluationException;
import org.eclipse.acceleo.query.runtime.EvaluationResult;
import org.eclipse.acceleo.query.runtime.IQueryBuilderEngine.AstResult;
import org.eclipse.acceleo.query.runtime.impl.QueryEvaluationEngine;
import org.eclipse.acceleo.query.runtime.IQueryEnvironment;
import org.eclipse.acceleo.query.runtime.IQueryEvaluationEngine;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.NotificationWrapper;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecoretools.ale.core.interpreter.services.DynamicEObjectServices;
import org.eclipse.emf.ecoretools.ale.core.parser.visitor.ModelBuilder;
import org.eclipse.emf.ecoretools.ale.core.interpreter.RuntimeInstanceHelper;
import org.eclipse.emf.ecoretools.ale.implementation.Attribute;
import org.eclipse.emf.ecoretools.ale.implementation.ExtendedClass;
import org.eclipse.emf.ecoretools.ale.implementation.ModelUnit;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;

/**
 * This class manages dynamic attributes for EObjects.
 */
public class DynamicFeatureRegistry {
	
	List<ModelUnit> allImplemModels;
	Map<EClass,EClass> baseToRuntime;
	
	Map<EObject,EObject> instanceToRuntime; //instance -> runtime content
	Map<EObject,EObject> runtimeToInstance;
	
	IQueryEnvironment queryEnvironment;
	
	private ResourceSet resourceSet;
	private Resource dynamicResource;
	private Resource runtimeResource;
	private Resource definitionsResource;
	
	public DynamicFeatureRegistry (List<ModelUnit> allImplemModels, List<EClass> domain){
		this.allImplemModels = allImplemModels;
		this.baseToRuntime = RuntimeInstanceHelper.getBaseToRuntime(allImplemModels,domain);
		instanceToRuntime = new HashMap<EObject,EObject>();
		runtimeToInstance = new HashMap<EObject,EObject>();
	}
	
	/**
	 * Returns the value of the specified feature on the specified object.
	 * Attributes declared in EClasses take priority over dynamic attributes. 
	 * The object must be an
	 * {@link EObject} or a {@link Set}, {@link List} of {@link EObject}.
	 * 
	 * @param context
	 *            the object in which to read the feature.
	 * @param featureName
	 *            the name of the feature to read.
	 * @param diagnostic
	 *            The status to update in case of warnings or errors during this call.
	 * @return the value of the specified feature in the specified object.
	 */
	public Object aqlFeatureAccess(EObject context, String featureName) {
		Object result;

		if (context == null) {
			String message = String.format(DynamicEObjectServices.NON_EOBJECT_FEATURE_ACCESS, featureName, "null");
			throw new AcceleoQueryEvaluationException(message);
		} else {
			EClass eClass = (context).eClass();
			EStructuralFeature feature = eClass.getEStructuralFeature(featureName);
			if (feature == null) {
				result = getDynamicFeatureValue(context,featureName);
			} else {
				result = (context).eGet(feature);
			}
		}

		return result;
	}
	
	private Object getDynamicFeatureValue(EObject instance, String featureName) {
		
		EObject extendedInstance = getOrCreateRuntimeExtension(instance);
		
		if(extendedInstance != null) {
			EStructuralFeature feature = extendedInstance.eClass().getEStructuralFeature(featureName);
			if(feature != null) {
				return extendedInstance.eGet(feature);
			}
		}
		else {
			//TODO: error
		}
		String message = String.format(DynamicEObjectServices.UNKNOWN_FEATURE, featureName, instance.eClass().getName());
		throw new AcceleoQueryEvaluationException(message);
	}
	
	public void setDynamicFeatureValue(EObject instance, String featureName, Object newValue) {
		EObject extendedInstance = getOrCreateRuntimeExtension(instance);
		
		if(extendedInstance != null) {
			EStructuralFeature feature = extendedInstance.eClass().getEStructuralFeature(featureName);
			if(feature != null) {
				if(newValue instanceof List) {
					BasicEList<EObject> newList = new BasicEList<EObject>((List)newValue);
					extendedInstance.eSet(feature, newList);
				}
				else {
					extendedInstance.eSet(feature, newValue);
				}
			}
			else {
				//TODO: error feature not found
			}
		}
		else {
			//TODO: error
		}
	}
	
	public void insertDynamicFeatureValue(EObject instance, String featureName, Object newValue) {
		
		EObject extendedInstance = getOrCreateRuntimeExtension(instance);
		
		if(extendedInstance != null) {
			EStructuralFeature feature = extendedInstance.eClass().getEStructuralFeature(featureName);
			if(feature != null) {
				Object featureValue = extendedInstance.eGet(feature);
				if(featureValue instanceof List){
					((List)featureValue).add(newValue);
				}
				else if(featureValue instanceof String){
					String concat = featureValue + "" + newValue;
					extendedInstance.eSet(feature, concat);
				}
				else if(featureValue instanceof Integer && newValue instanceof Integer) {
					Integer sum = (Integer) featureValue + (Integer) newValue;
					extendedInstance.eSet(feature, sum);
				}
			}
			else {
				//TODO: error feature not found
			}
		}
		else {
			//TODO: error
		}
	}
	
	public void removeDynamicFeatureValue(EObject instance, String featureName, Object newValue) {
		
		EObject extendedInstance = getOrCreateRuntimeExtension(instance);
		
		if(extendedInstance != null) {
			EStructuralFeature feature = extendedInstance.eClass().getEStructuralFeature(featureName);
			if(feature != null) {
				Object featureValue = extendedInstance.eGet(feature);
				if(featureValue instanceof List){
					((List)featureValue).remove(newValue);
				}
				else if(featureValue instanceof Integer && newValue instanceof Integer) {
					Integer substraction = (Integer) featureValue - (Integer) newValue;
					extendedInstance.eSet(feature, substraction);
				}
				else {
					//TODO: Error
				}
			}
			else {
				//TODO: error feature not found
			}
		}
		else {
			//TODO: error
		}
	}
	
	/**
	 * returns the first feature in the extended class hieararchy that applies to this type that declares a feature with this name
	 * @param type
	 * @param featureName
	 * @return
	 */
	public Optional<Attribute> findFeature(EClass type, String featureName) {
		// FIXME: does it deal with all situations ?  for eg. inheritance between open classes ?
		List<ExtendedClass> extendedClasses = allImplemModels
			.stream()
			.flatMap(m -> m.getClassExtensions().stream())
			.filter(cls -> cls.getBaseClass().isSuperTypeOf(type)).collect(Collectors.toList());
		for(ExtendedClass extendedClass : extendedClasses) {
			Optional<Attribute> featureDeclaration = extendedClass.getAttributes().stream()
				.filter(attr -> attr.getFeatureRef().getName().equals(featureName))
				.findFirst();
			if(featureDeclaration.isPresent()) return featureDeclaration;
		}
		return Optional.empty();
	}
	
	private Optional<Attribute> findOpposite(Attribute feature) {
		
		if(feature.getFeatureRef() instanceof EReference) {
			EReference ref = (EReference) feature.getFeatureRef();
		}
		
		return Optional.empty();
	}
	
	private EObject getOrCreateRuntimeExtension(EObject instance) {
		EObject extendedInstance = instanceToRuntime.get(instance);
		
		if(extendedInstance == null) {
			EClass runtimeExtensionClass = getRuntimeExtensionClass(instance);
			if(runtimeExtensionClass != null){
				extendedInstance = EcoreUtil.create(runtimeExtensionClass);
				instanceToRuntime.put(instance,extendedInstance);
				runtimeToInstance.put(extendedInstance, instance);
				
				// Register Adapters for the Runtime part and pretend that
				// notifications comes from the instance object
				for(Adapter adapter : instance.eAdapters()) {
					if(adapter instanceof EContentAdapter) {
						Adapter runtimeAdapter = new Adapter(){
							final Adapter originalAdapter = adapter;
							@Override
							public void notifyChanged(Notification notification) {
								originalAdapter.notifyChanged(new NotificationWrapper(instance,notification));
							}
							
							@Override
							public Notifier getTarget() {
								return originalAdapter.getTarget();
							}
							
							@Override
							public void setTarget(Notifier newTarget) {
								//keep original target
							}
							
							@Override
							public boolean isAdapterForType(Object type) {
								return originalAdapter.isAdapterForType(type);
							}
						};
						extendedInstance.eAdapters().add(runtimeAdapter);
					}
				}
			}
		}
		
		return extendedInstance;
	}
	
	/**
	 * Return an EObject containing values of runtime features defined
	 * in ExtendedClasses appliables on {@link instance}
	 */
	public Optional<EObject> getRuntimeExtension(EObject instance) {
		EObject extendedInstance = instanceToRuntime.get(instance);
		return Optional.ofNullable(extendedInstance);
	}
	
	/**
	 * Return the extended class of an instance
	 */
	public EClass getRuntimeExtensionClass(EObject instance) {
		return baseToRuntime.get(instance.eClass());
	}
	
	/**
	 * If {@link eObject} is an extension, return the corresponding instance.
	 * Return itself otherwise
	 */
	public EObject getInstanceOrSelf(EObject eObject) {
		EObject res = runtimeToInstance.get(eObject);
		if(res != null) {
			return res;
		}
		else {
			return eObject;
		}
	}
	
    /**
     * Initialize dynamic feature for each EObject of the model
     */
    public void dynamicModelConstructor(Set<EObject> model, IQueryEnvironment queryEnvironment) {
		this.queryEnvironment = queryEnvironment;
		IQueryEvaluationEngine aqlEngine = new QueryEvaluationEngine(queryEnvironment);
		model.forEach(obj -> {
			List<ExtendedClass> extensions = findExtensionFor(obj);
			init(obj, extensions, aqlEngine);
		});
	}
    
    private List<ExtendedClass> findExtensionFor(EObject instance) {
    	return 
    		allImplemModels
	    	.stream()
	    	.flatMap(mb -> mb.getClassExtensions().stream())
	    	.filter(xtdCls -> xtdCls.getBaseClass().isSuperTypeOf(instance.eClass()))
	    	.collect(Collectors.toList());
    }
    
    private void init(EObject instance, List<ExtendedClass> extensions, IQueryEvaluationEngine aqlEngine) {
    	EObject extendedInstance = getOrCreateRuntimeExtension(instance);
    	
    	Map<String,Object> scope = new HashMap<String,Object>();
    	scope.put("self", instance);
    	
    	if(extendedInstance != null) {
			// Look for the root element of the model
			if (instance.eContainer() == null && instance.eResource() != null) {
				this.resourceSet = instance.eResource().getResourceSet();
				this.dynamicResource = this.resourceSet.createResource(URI.createURI("dummy:/dynamic.xmi"));
				this.runtimeResource = this.resourceSet.createResource(URI.createURI("dummy:/runtime.xmi"));
				this.definitionsResource = this.resourceSet.createResource(URI.createURI("dummy:/definitions.xmi"));
				
				TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(this.resourceSet);
				if (domain == null) {
					domain = TransactionalEditingDomain.Factory.INSTANCE.createEditingDomain(this.resourceSet);
				}
				domain.getCommandStack().execute(new RecordingCommand(domain) {
					@Override
					protected void doExecute() {
						// Store the dynamic instance of the root element in a resource
						dynamicResource.getContents().add(extendedInstance);
						// Store the runtime package in a resource
						runtimeResource.getContents().add(extendedInstance.eClass().eContainer());
						// Store the package of class definitions in a resource
						definitionsResource.getContents().add(queryEnvironment.getEPackageProvider().getRegisteredEPackages().stream()
								.filter(p -> p.getNsURI().startsWith(ModelBuilder.RUNTIME_ALE_NSURI)).findFirst().get());
					}
				});
			}
    	}
		else {
			//TODO: error
		}
    	
    	extensions
			.stream()
			.flatMap(xtdCls -> xtdCls.getAttributes().stream())
			.filter(attr -> attr.getInitialValue() != null)
			.forEach(attr -> {
				AstResult dummyAstResult = new AstResult(attr.getInitialValue(), new HashMap(), new HashMap(), new ArrayList(), new BasicDiagnostic());
				EvaluationResult result = aqlEngine.eval(dummyAstResult, scope); //TODO: forward diagnotic
				Object value = result.getResult();
				
					EStructuralFeature feature = extendedInstance.eClass().getEStructuralFeature(attr.getFeatureRef().getName());
					if(feature != null) {
						if(feature.isMany()){
							List featureValue = (List) extendedInstance.eGet(feature);
							if(value instanceof Collection){
								featureValue.addAll((Collection) value);
							}
							else{
								featureValue.add(value);
							}
						}
						else {
							extendedInstance.eSet(feature, value); //TODO: check type?
						}
					}
					else {
						//TODO: error feature not found
					}
				
			});
    }
}