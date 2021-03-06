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
package org.eclipse.emf.ecoretools.ale.core.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.acceleo.query.ast.Expression;
import org.eclipse.acceleo.query.runtime.IValidationMessage;
import org.eclipse.acceleo.query.runtime.ValidationMessageLevel;
import org.eclipse.acceleo.query.runtime.impl.ValidationMessage;
import org.eclipse.acceleo.query.validation.type.AbstractCollectionType;
import org.eclipse.acceleo.query.validation.type.ClassType;
import org.eclipse.acceleo.query.validation.type.EClassifierType;
import org.eclipse.acceleo.query.validation.type.ICollectionType;
import org.eclipse.acceleo.query.validation.type.IType;
import org.eclipse.acceleo.query.validation.type.NothingType;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecoretools.ale.implementation.Attribute;
import org.eclipse.emf.ecoretools.ale.implementation.BehavioredClass;
import org.eclipse.emf.ecoretools.ale.implementation.Block;
import org.eclipse.emf.ecoretools.ale.implementation.ConditionalBlock;
import org.eclipse.emf.ecoretools.ale.implementation.ExtendedClass;
import org.eclipse.emf.ecoretools.ale.implementation.FeatureAssignment;
import org.eclipse.emf.ecoretools.ale.implementation.FeatureInsert;
import org.eclipse.emf.ecoretools.ale.implementation.FeatureRemove;
import org.eclipse.emf.ecoretools.ale.implementation.ForEach;
import org.eclipse.emf.ecoretools.ale.implementation.If;
import org.eclipse.emf.ecoretools.ale.implementation.ImplementationPackage;
import org.eclipse.emf.ecoretools.ale.implementation.Method;
import org.eclipse.emf.ecoretools.ale.implementation.ModelUnit;
import org.eclipse.emf.ecoretools.ale.implementation.RuntimeClass;
import org.eclipse.emf.ecoretools.ale.implementation.Statement;
import org.eclipse.emf.ecoretools.ale.implementation.VariableAssignment;
import org.eclipse.emf.ecoretools.ale.implementation.VariableDeclaration;
import org.eclipse.emf.ecoretools.ale.implementation.While;

import com.google.common.collect.Lists;

public class TypeValidator implements IValidator {

	public static final String INCOMPATIBLE_TYPE = "Expected %s but was %s";
	public static final String BOOLEAN_TYPE = "Expected ecore::EBoolean but was %s";
	public static final String COLLECTION_TYPE = "Expected Collection but was %s";
	public static final String VOID_RESULT_ASSIGN = "'result' is assigned in void operation";
	public static final String EXTENDS_ITSELF = "Reopened %s is extending itself";
	public static final String INDIRECT_EXTENSION = "Can't extend %s since it is not a direct super type of %s";
	
	BaseValidator base;
	
	public void setBase(BaseValidator base) {
		this.base = base;
	}
	
	@Override
	public List<IValidationMessage> validateModelBehavior(List<ModelUnit> units) {
		return new ArrayList<IValidationMessage>();
	}
	
	@Override
	public List<IValidationMessage> validateModelUnit(ModelUnit unit) {
		return new ArrayList<IValidationMessage>();
	}
	
	@Override
	public List<IValidationMessage> validateExtendedClass(ExtendedClass xtdClass) {
		List<IValidationMessage> msgs = new ArrayList<IValidationMessage>();
		
		msgs.addAll(validateBehavioredClass(xtdClass));
		
		if(isExtendingItself(xtdClass)) {
			msgs.add(new ValidationMessage(
					ValidationMessageLevel.ERROR,
					String.format(EXTENDS_ITSELF, getQualifiedName(xtdClass.getBaseClass())),
					base.getStartOffset(xtdClass),
					base.getEndOffset(xtdClass)
					));
		}
		
		EClass baseCls = xtdClass.getBaseClass();
		EList<EClass> superTypes = baseCls.getESuperTypes();
		
		List<EClass> extendsBaseClasses =
				xtdClass
				.getExtends()
				.stream()
				.map(xtd -> xtd.getBaseClass())
				.collect(Collectors.toList());
		
		extendsBaseClasses.forEach(superBase -> {
			if(!superTypes.contains(superBase) && baseCls != superBase) {
				msgs.add(new ValidationMessage(
						ValidationMessageLevel.ERROR,
						String.format(INDIRECT_EXTENSION, getQualifiedName(superBase), getQualifiedName(baseCls)),
						this.base.getStartOffset(xtdClass),
						this.base.getEndOffset(xtdClass)
						));
			}
		});
		
		return msgs;
	}
	
	@Override
	public List<IValidationMessage> validateRuntimeClass(RuntimeClass classDef) {
		List<IValidationMessage> msgs = new ArrayList<IValidationMessage>();
		
		msgs.addAll(validateBehavioredClass(classDef));
		
		return msgs;
	}
	
	private List<IValidationMessage> validateBehavioredClass(BehavioredClass clazz) {
		List<IValidationMessage> msgs = new ArrayList<IValidationMessage>();
		
		clazz
		.getAttributes()
		.stream()
		.filter(att -> att.getInitialValue() != null)
		.forEach(att -> {
			Set<IType> inferredTypes = base.getPossibleTypes(att.getInitialValue());
			EClassifierType declaredType = new EClassifierType(base.getQryEnv(), att.getFeatureRef().getEType());
			Optional<IType> existResult = inferredTypes.stream().filter(type -> declaredType.isAssignableFrom(type)).findAny();
			if(!existResult.isPresent()){
				String types = 
					inferredTypes
					.stream()
					.map(type -> getQualifiedName(type))
					.collect(Collectors.joining(",","[","]"));
				msgs.add(new ValidationMessage(
						ValidationMessageLevel.ERROR,
						String.format(INCOMPATIBLE_TYPE, getQualifiedName(att.getFeatureRef().getEType()),types),
						base.getStartOffset(att),
						base.getEndOffset(att)
						));
			}
		});
		
		return msgs;
	}
	
	@Override
	public List<IValidationMessage> validateMethod(Method mtd) {
		return new ArrayList<IValidationMessage>();
	}
	
	@Override
	public List<IValidationMessage> validateFeatureAssignment(FeatureAssignment featAssign) {
		return validateAssignment(featAssign, featAssign.getTarget(), featAssign.getTargetFeature(), featAssign.getValue(), false);
	}
	
	@Override
	public List<IValidationMessage> validateFeatureInsert(FeatureInsert featInsert) {
		return validateAssignment(featInsert, featInsert.getTarget(), featInsert.getTargetFeature(), featInsert.getValue(), true);
	}
	
	@Override
	public List<IValidationMessage> validateFeatureRemove(FeatureRemove featRemove) {
		
		return validateAssignment(featRemove, featRemove.getTarget(), featRemove.getTargetFeature(), featRemove.getValue(), true);
	}
	
	private List<IValidationMessage> validateAssignment(Statement stmt, Expression targetExp, String featureName, Expression valueExp, boolean isInsert) {
		List<IValidationMessage> msgs = new ArrayList<IValidationMessage>();
		
		/*
		 * Collect feature types
		 */
		Set<IType> targetTypes = base.getPossibleTypes(targetExp);
		Set<EClassifierType> featureTypes = new HashSet<EClassifierType>();
		boolean isCollection = false;
		for(IType type: targetTypes){
			if(type.getType() instanceof EClass){
				EClass realType = (EClass) type.getType();
				EStructuralFeature feature = realType.getEStructuralFeature(featureName);
				
				if(feature  != null){ //static features
					EClassifierType featureType = new EClassifierType(base.getQryEnv(), feature.getEType());
					featureTypes.add(featureType);
					isCollection = feature.isMany();
				}
				else { //runtime features
					List<ExtendedClass> extensions = base.findExtensions(realType);
					
					Optional<Attribute> foundDynamicAttribute = //FIXME: take inheritance in account
						extensions
						.stream()
						.flatMap(xtdCls -> xtdCls.getAttributes().stream())
						.filter(field -> field.getFeatureRef().getName().equals(featureName))
						.findFirst();
					if(foundDynamicAttribute.isPresent()) {
						EClassifierType featureType = new EClassifierType(base.getQryEnv(), foundDynamicAttribute.get().getFeatureRef().getEType());
						featureTypes.add(featureType);
						isCollection = foundDynamicAttribute.get().getFeatureRef().isMany();
					}
				}
			}
		}
		
		/*
		 * Check targetExp.featureName is collection
		 */
		if(isInsert && !isCollection && !featureTypes.isEmpty()) {
			String inferredToString = 
					featureTypes
					.stream()
					.map(type -> getQualifiedName(type))
					.collect(Collectors.joining(",","[","]"));
			msgs.add(new ValidationMessage(
					ValidationMessageLevel.ERROR,
					String.format(COLLECTION_TYPE,inferredToString),
					base.getStartOffset(targetExp),
					base.getEndOffset(targetExp)
					));
		}
		
		/*
		 * Check assignment type
		 */
		if(!featureTypes.isEmpty()) {
			
			Set<IType> inferredTypes = base.getPossibleTypes(valueExp);

			if(!isInsert && isCollection) {
				for(IType inferredType: inferredTypes){
					if(inferredType instanceof AbstractCollectionType) {
						IType collectionType = ((AbstractCollectionType)inferredType).getCollectionType();
					}
				}
				boolean isAnyAssignable = false;
				for(IType featureType: featureTypes) {
					Optional<IType> existResult = 
							inferredTypes
							.stream()
							.filter(t -> t instanceof AbstractCollectionType)
							.map(t -> ((AbstractCollectionType)t).getCollectionType())
							.filter(t -> featureType.isAssignableFrom(t))
							.findAny();
					if(existResult.isPresent()){
						isAnyAssignable = true;
						break;
					}
				}
				if(!isAnyAssignable) {
					String inferredToString = 
							inferredTypes
							.stream()
							.map(type -> getQualifiedName(type))
							.collect(Collectors.joining(",","[","]"));
					String featureToString = 
							featureTypes
							.stream()
							.map(type -> getQualifiedName(type.getType()))
							.collect(Collectors.joining(",","(",")"));
					msgs.add(new ValidationMessage(
							ValidationMessageLevel.ERROR,
							String.format(INCOMPATIBLE_TYPE,"[Collection"+featureToString+"]",inferredToString),
							base.getStartOffset(stmt),
							base.getEndOffset(stmt)
							));
				}
			}
			else {
				boolean isAnyAssignable = false;
				for(IType featureType: featureTypes){
					Optional<IType> existResult = inferredTypes
							.stream()
							.filter(t -> 
								featureType.isAssignableFrom(t)
								|| (featureType.getType()  == EcorePackage.eINSTANCE.getEEList() && t instanceof AbstractCollectionType )) // TODO should be able to be more precise
							.findAny();
					if(existResult.isPresent()){
						isAnyAssignable = true;
						break;
					}
				}
				if(!isAnyAssignable){
					String inferredToString = 
							inferredTypes
							.stream()
							.map(type -> getQualifiedName(type))
							.collect(Collectors.joining(",","[","]"));
					String featureToString = 
							featureTypes
							.stream()
							.map(type -> getQualifiedName(type.getType()))
							.collect(Collectors.joining(",","[","]"));
					
					msgs.add(new ValidationMessage(
							ValidationMessageLevel.ERROR,
							String.format(INCOMPATIBLE_TYPE,featureToString,inferredToString),
							base.getStartOffset(stmt),
							base.getEndOffset(stmt)
							));
				}
				
			}
			
		}
		
		return msgs;
	}
	
	@Override
	public List<IValidationMessage> validateVariableAssignment(VariableAssignment varAssign) {
		List<IValidationMessage> msgs = new ArrayList<IValidationMessage>();
		
		Set<IType> declaringTypes = findDeclaredTypes(varAssign);
		if(varAssign.getName().equals("result")) {
			Method op = base.getContainingOperation(varAssign);
			EOperation eOp = ((Method)op).getOperationRef();
			if(eOp != null) {
				boolean isVoidOp = eOp.getEType() == null && eOp.getEGenericType() == null;
				
				if(isVoidOp) {
					msgs.add(new ValidationMessage(
							ValidationMessageLevel.ERROR,
							String.format(VOID_RESULT_ASSIGN,varAssign.getName()),
							base.getStartOffset(varAssign),
							base.getEndOffset(varAssign)
							));
				}
				else {
					
					Optional<EOperation> eOperation = findContainingEOperation(varAssign);
					EClassifier returnType = eOp.getEType();
					EClassifierType declaredType = new EClassifierType(base.getQryEnv(), returnType);
					Set<IType> inferredTypes = base.getPossibleTypes(varAssign.getValue());
					Optional<IType> matchingType = inferredTypes
							.stream()
							.filter(inferredType -> 
								isInferredTypeCompatibleForResultVar(declaredType, eOperation, inferredType))
							.findAny();
					if(!matchingType.isPresent()) {
						String types = 
								inferredTypes
								.stream()
								.map(type -> getQualifiedName(type))
								.collect(Collectors.joining(",","[","]"));
						msgs.add(new ValidationMessage(
								ValidationMessageLevel.ERROR,
								String.format(INCOMPATIBLE_TYPE,"["+getTypeQualifiedNameForOperationResult(declaredType, eOperation)+"]",types),
								base.getStartOffset(varAssign),
								base.getEndOffset(varAssign)
								));
					}
				}
			}
		}
		else if(declaringTypes != null && !varAssign.getName().equals("self")) {
			Set<IType> inferredTypes = base.getPossibleTypes(varAssign.getValue());
			
			if(inferredTypes != null && !declaringTypes.isEmpty()) {
				
				Optional<VariableDeclaration> declaration = findDeclaration(varAssign);
				Optional<IType> existResult = 
						declaringTypes
						.stream()
						.filter(declType -> 
							inferredTypes
								.stream()
								.filter(inferredType ->
									isInferredTypeCompatibleForVar(declType, declaration, inferredType))
								.findAny()
								.isPresent()
							)
						.findAny();
				if(!existResult.isPresent()){
					String declaredToString = 
							declaringTypes
							.stream()
							.map(type -> getTypeQualifiedNameForVar(type,declaration))
							.collect(Collectors.joining(",","[","]"));
					String inferredToString = 
							inferredTypes
							.stream()
							.map(type -> getQualifiedName(type))
							.collect(Collectors.joining(",","[","]"));
					
					msgs.add(new ValidationMessage(
							ValidationMessageLevel.ERROR,
							String.format(INCOMPATIBLE_TYPE,declaredToString,inferredToString),
							base.getStartOffset(varAssign),
							base.getEndOffset(varAssign)
							));
				}
				
			}
		}
		
		return msgs;
	}

	private String getTypeQualifiedNameForOperationResult(IType declaredType, Optional<EOperation> eOperation) {
		if( declaredType.getType() == EcorePackage.eINSTANCE.getEEList()) {
			// collection
			if(eOperation.isPresent()) {
				if(eOperation.get().getEGenericType().getETypeArguments().isEmpty()) {
					return "Collection(?)";
				} else {
					EClassifierType varTypeParam = new EClassifierType(base.getQryEnv(), eOperation.get().getEGenericType().getETypeArguments().get(0).getEClassifier());
					return "Collection("+getQualifiedName(varTypeParam)+")";
				}
			}
		} 
		return getQualifiedName(declaredType);
	}
	private String getTypeQualifiedNameForVar(IType declaredType, Optional<VariableDeclaration> declaration) {
		if( declaredType.getType() == EcorePackage.eINSTANCE.getEEList()) {
			if(declaration.isPresent()) {
				// collection
				EClassifierType varTypeParam = new EClassifierType(base.getQryEnv(), declaration.get().getTypeParameter());
				//return getQualifiedName(declaration.get().getType())+"("+getQualifiedName(varTypeParam)+")";
				return "Collection("+getQualifiedName(varTypeParam)+")";
				
			}
		} 
		return getQualifiedName(declaredType);
	}
	/**
	 * in case of collection, extract information from the Var declaration to check if the inferred type is compatible
	 * @param declaredType
	 * @param declaration
	 * @param inferredType
	 * @return
	 */
	private boolean isInferredTypeCompatibleForVar(IType declaredType, Optional<VariableDeclaration> declaration, IType inferredType) {
		if(declaredType.getType() == EcorePackage.eINSTANCE.getEEList() && inferredType instanceof AbstractCollectionType) {
			if(declaration.isPresent()) {
				EClassifierType varTypeParam = new EClassifierType(base.getQryEnv(), declaration.get().getTypeParameter());
				IType collectionType = ((AbstractCollectionType)inferredType).getCollectionType();
				return varTypeParam.isAssignableFrom(collectionType) || collectionType instanceof NothingType;
			}
		} 
		return declaredType.isAssignableFrom(inferredType);
	}
	/**
	 * in case of collection, extract information from the EOperation declaration to check if the inferred type is compatible
	 * @param declaredType
	 * @param eOperation
	 * @param inferredType
	 * @return
	 */
	private boolean isInferredTypeCompatibleForResultVar(IType declaredType, Optional<EOperation> eOperation, IType inferredType) {
		if(declaredType.getType() == EcorePackage.eINSTANCE.getEEList() && inferredType instanceof AbstractCollectionType) {
			if(eOperation.isPresent() && inferredType instanceof AbstractCollectionType) {
				IType collectionType = ((AbstractCollectionType)inferredType).getCollectionType();
				if(eOperation.get().getEGenericType().getETypeArguments().isEmpty()) {
					// no type argument provided in the EOperation definition ! accept any collection content
					return true;
					
				} else if(collectionType instanceof NothingType){
					return true;
				} else {
					EClassifierType varTypeParam = new EClassifierType(base.getQryEnv(), eOperation.get().getEGenericType().getETypeArguments().get(0).getEClassifier());
					return varTypeParam.isAssignableFrom(collectionType);
				}
			}
		} 
		return declaredType.isAssignableFrom(inferredType);
	}
	
	
	@Override
	public List<IValidationMessage> validateVariableDeclaration(VariableDeclaration varDecl) {
		List<IValidationMessage> msgs = new ArrayList<IValidationMessage>();
		
		if(varDecl.getInitialValue() != null) {
			
			EClassifier declaredType = varDecl.getType();
			Set<IType> inferredTypes = base.getPossibleTypes(varDecl.getInitialValue());
			if(inferredTypes != null) {
				
				if(declaredType == EcorePackage.eINSTANCE.getEEList()) {
					EClassifierType varTypeParam = new EClassifierType(base.getQryEnv(), varDecl.getTypeParameter());
					Optional<IType> existResult =
							inferredTypes
							.stream()
							.filter(t -> t instanceof AbstractCollectionType)
							.map(t -> ((AbstractCollectionType)t).getCollectionType())
							.filter(t -> varTypeParam.isAssignableFrom(t) || t instanceof NothingType)
							.findAny();
					if(!existResult.isPresent()) {
						String inferredToString = 
								inferredTypes
								.stream()
								.map(type -> getQualifiedName(type))
								.collect(Collectors.joining(",","[","]"));

						msgs.add(new ValidationMessage(
								ValidationMessageLevel.ERROR,
								String.format(INCOMPATIBLE_TYPE,"Collection("+getQualifiedName(varTypeParam)+")",inferredToString),
								base.getStartOffset(varDecl),
								base.getEndOffset(varDecl)
								));
					}
				}
				else {
					EClassifierType varType = new EClassifierType(base.getQryEnv(), varDecl.getType());
					Optional<IType> existResult =
							inferredTypes
							.stream()
							.filter(t -> varType.isAssignableFrom(t) 
									|| (t instanceof AbstractCollectionType && varDecl.getType() == EcorePackage.eINSTANCE.getEEList())) //TODO: check collection type parameter
							.findAny();
					if(!existResult.isPresent()) {
						String inferredToString = 
								inferredTypes
								.stream()
								.map(type -> getQualifiedName(type))
								.collect(Collectors.joining(",","[","]"));
						
						msgs.add(new ValidationMessage(
								ValidationMessageLevel.ERROR,
								String.format(INCOMPATIBLE_TYPE,getQualifiedName(varDecl.getType()),inferredToString),
								base.getStartOffset(varDecl),
								base.getEndOffset(varDecl)
								));
					}
				}
			}
		}
		
		return msgs;
	}
	
	@Override
	public List<IValidationMessage> validateForEach(ForEach loop) {
		List<IValidationMessage> msgs = new ArrayList<IValidationMessage>();
		
		/*
		 * Check expression is collection
		 */
		Optional<IType> existResult = 
			base
			.getPossibleTypes(loop.getCollectionExpression())
			.stream()
			.filter(type -> type instanceof ICollectionType)
			.findAny();
		if(!existResult.isPresent()) {
			String inferredToString = 
					base
					.getPossibleTypes(loop.getCollectionExpression())
					.stream()
					.map(type -> getQualifiedName(type))
					.collect(Collectors.joining(",","[","]"));
			msgs.add(new ValidationMessage(
					ValidationMessageLevel.ERROR,
					String.format(COLLECTION_TYPE,inferredToString),
					base.getStartOffset(loop.getCollectionExpression()),
					base.getEndOffset(loop.getCollectionExpression())
					));
		}
		
		return msgs;
	}
	
	@Override
	public List<IValidationMessage> validateIf(If ifStmt) {
		List<IValidationMessage> res = new ArrayList<>();
		for (ConditionalBlock cBlock : ifStmt.getBlocks()) {
			res.addAll(validateIsBoolean(cBlock.getCondition()));
		}
		return res;
	}
	
	@Override
	public List<IValidationMessage> validateWhile(While loop) {
		return validateIsBoolean(loop.getCondition());
	}
	
	private List<IValidationMessage> validateIsBoolean(Expression exp) {
		List<IValidationMessage> msgs = new ArrayList<IValidationMessage>();

		Set<IType> selectorTypes = base.getPossibleTypes(exp);
		boolean onlyNotBoolean = true;
		final IType booleanObjectType = new ClassType(base.getQryEnv(), Boolean.class);
		final IType booleanType = new ClassType(base.getQryEnv(), boolean.class);
		for (IType type : selectorTypes) {
			final boolean assignableFrom = booleanObjectType.isAssignableFrom(type)
					|| booleanType.isAssignableFrom(type);
			onlyNotBoolean = onlyNotBoolean && !assignableFrom;
		}
		if(onlyNotBoolean){
			String inferredToString = 
					selectorTypes
					.stream()
					.map(type -> getQualifiedName(type))
					.collect(Collectors.joining(",","[","]"));
			msgs.add(new ValidationMessage(
					ValidationMessageLevel.ERROR,
					String.format(BOOLEAN_TYPE,inferredToString),
					base.getStartOffset(exp),
					base.getEndOffset(exp)
					));
		}
		
		return msgs;
	}
	
	private boolean isExtendingItself(ExtendedClass xtdClass) {
		
		List<ExtendedClass> todo = Lists.newArrayList(xtdClass);
		List<ExtendedClass> done = Lists.newArrayList();
		
		while(!todo.isEmpty()) {
			ExtendedClass current = todo.get(0);
			
			if(done.contains(current)) {
				return true;
			}
			
			todo.addAll(current.getExtends());
			
			done.add(current);
			todo.remove(0);
		}
		
		return false;
	}
	
	
	/**
	 * find the EOperation that contain this statement
	 */
	private Optional<EOperation> findContainingEOperation(Statement statement){

		// find containing method then EOperation
		EObject current = statement.eContainer();
		EClass type = ImplementationPackage.eINSTANCE.getMethod();
		while (current != null && !type.isSuperTypeOf(current.eClass()) && !type.isInstance(current)) {
			current = current.eContainer();
		}
		if (current != null && (type.isSuperTypeOf(current.eClass()) || type.isInstance(current))) {
			return Optional.ofNullable(((Method)current).getOperationRef());
		} 
		
		return Optional.empty();
	}
	
	private Optional<VariableDeclaration> findDeclaration(VariableAssignment varAssign) {
		
		String variableName = varAssign.getName();
		
		EObject currentObject = varAssign;
		EObject currentScope = varAssign.eContainer();
		
		while(currentScope != null) {
			if(currentScope instanceof Block) {
				Block block = (Block) currentScope;
				int index = block.getStatements().indexOf(currentObject);
				if(index != -1) {
					Optional<VariableDeclaration> candidate =
						block.getStatements()
						.stream()
						.limit(index)
						.filter(stmt -> stmt instanceof VariableDeclaration)
						.map(stmt -> (VariableDeclaration) stmt)
						.filter(varDecl -> varDecl.getName().equals(variableName))
						.findFirst();
					if(candidate.isPresent()) {
						return candidate;
					}
				}
			}
			
			currentObject = currentScope;
			currentScope = currentScope.eContainer();
		}
		
		return Optional.empty();
	}
	
	private Set<IType> findDeclaredTypes(VariableAssignment varAssign) {
		
		Set<IType> res = new HashSet<IType>();
		String variableName = varAssign.getName();
		
		// Look at extended EClass attributes
		EObject currentObject = varAssign;
		EObject currentScope = varAssign.eContainer();
		
		while(currentScope != null) {
			
			// Look at previous statement in the same block
			if(currentScope instanceof Block) {
				Block block = (Block) currentScope;
				int index = block.getStatements().indexOf(currentObject);
				if(index != -1) {
					Optional<VariableDeclaration> candidate =
						block.getStatements()
						.stream()
						.limit(index)
						.filter(stmt -> stmt instanceof VariableDeclaration)
						.map(stmt -> (VariableDeclaration) stmt)
						.filter(varDecl -> varDecl.getName().equals(variableName))
						.findFirst();
					if(candidate.isPresent()) {
						EClassifier type = candidate.get().getType();
						res.add(new EClassifierType(base.getQryEnv(), type));
						return res;
					}
				}
			}
			
			// Look at loop's variable
			else if(currentScope instanceof ForEach) {
				ForEach loop = (ForEach) currentScope;
				if(loop.getVariable().equals(variableName)) {
					Set<IType> inferredTypes = base.getPossibleTypes(loop.getCollectionExpression());
					return inferredTypes;
				}
			}
			
			// Look at class extension
			else if(currentScope instanceof BehavioredClass) {
				BehavioredClass cls = (BehavioredClass) currentScope;
				Optional<Attribute> candidate = cls.getAttributes().stream().filter(attr -> attr.getFeatureRef().getName().equals(variableName)).findFirst();
				if(candidate.isPresent()) {
					EClassifier type = candidate.get().getFeatureRef().getEType();
					res.add(new EClassifierType(base.getQryEnv(), type));
					return res;
				}
			}
			
			// Look at extended class
			else if(currentScope instanceof ExtendedClass) {
				ExtendedClass extension = (ExtendedClass) currentScope;
				Optional<EStructuralFeature> feature = 
						extension.getBaseClass().getEAllStructuralFeatures()
						.stream()
						.filter(feat -> feat.getName().equals(variableName))
						.findFirst();
				if(feature.isPresent()) {
					EClassifier type = feature.get().getEType();
					res.add(new EClassifierType(base.getQryEnv(), type));
					return res;
				}
			}
			
			currentObject = currentScope;
			currentScope = currentScope.eContainer();
		}
		
		return res;
	}
	
	private static String getQualifiedName(EClassifier cls) {
		return getQualifiedName(cls.getEPackage()) + "::" + cls.getName(); 
	}
	
	private static String getQualifiedName(EPackage pkg) {
		LinkedList<EPackage> pkgs = new LinkedList<>();
		EPackage current = pkg;
		while(current != null) {
			pkgs.addFirst(current);
			current = current.getESuperPackage();
		}
		
		return pkgs
			.stream()
			.map(p -> p.getName())
			.collect(Collectors.joining("::"));
	}
	
	private static String getQualifiedName(IType type) {
		
		if(type instanceof EClassifierType) {
			EClassifier cls = ((EClassifierType) type).getType();
			return getQualifiedName(cls);
		}
		
		return type.toString();
	}
}
