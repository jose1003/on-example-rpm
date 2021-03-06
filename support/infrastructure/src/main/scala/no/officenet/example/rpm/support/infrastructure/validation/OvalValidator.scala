package no.officenet.example.rpm.support.infrastructure.validation

/**
 * Copyright OfficeNet AS
 */

import net.sf.oval.configuration.Configurer
import net.sf.oval.internal.ContextCache
import java.lang.reflect.Method
import collection.JavaConversions._
import collection.mutable.HashMap
import net.sf.oval.localization.message.MessageResolver
import java.util.{MissingResourceException, Locale, Collections}
import org.springframework.context.i18n.LocaleContextHolder
import no.officenet.example.rpm.support.infrastructure.i18n.ResourceBundleHelper
import net.sf.oval.{Check, ConstraintViolation, Validator}
import net.sf.oval.context.OValContext
import org.hibernate.Hibernate

/**
 * Custom validator based on Oval's {@link Validator}}
 * <br/>
 * The following customizations are made:
 * <ul>
 * <li>
 * Refactored out i18n of messages to use the more flexible {@link MessageResources} from the Struts-1.1 framework.
 * Some cusomizations are done to MessageResources compared to the Struts-1.1 version, se {@link MessageResources} for details.
 * </li>
 * <li>
 * Better handling of the <code>{context}</code>-placeholder, allowing to translate what the context is using our own
 * ResourceBundleValidationContextRenderer which uses our own mechanism for getting the right locale
 */
class OvalValidator(configurers: java.util.Collection[Configurer])
	extends net.sf.oval.Validator(configurers) {

	net.sf.oval.Validator.setMessageResolver(OvalMessageResolver)
	net.sf.oval.Validator.setContextRenderer(RpmResourceBundleValidationContextRenderer.INSTANCE)

	def validateMethodReturnValue(validatedObject: AnyRef, method: Method, valueToValidate: Any): java.util.List[net.sf.oval.ConstraintViolation] = {
		val checksForMethod = getChecks(method)
		if (checksForMethod == null || checksForMethod.isEmpty) {
			return Collections.emptyList()
		}
		val violations = Validator.getCollectionFactory.createList[net.sf.oval.ConstraintViolation]()
		val ctx = ContextCache.getMethodReturnValueContext(method)
		for (check <- checksForMethod) {
			checkConstraint(violations, check, validatedObject, valueToValidate, ctx, null /* profiles */, false, false)
		}
		violations
	}

	override def checkConstraint(violations: java.util.List[ConstraintViolation], check: Check, validatedObject: AnyRef,
								 valueToValidate: Any, context: OValContext, profiles: Array[String],
								 isContainerValue: Boolean, ignoreTarget: Boolean) {
		if (Hibernate.isInitialized(valueToValidate)) {
			super.checkConstraint(violations, check, validatedObject, valueToValidate, context, profiles, isContainerValue, ignoreTarget)
		}
	}

}

object OvalMessageResolver extends MessageResolver {

	def getInstance = this

	var resourceBundles: java.util.Set[String] = Collections.emptySet()
	val cache: HashMap[String, String] = HashMap.empty

	def clear() {
		cache.synchronized(cache.clear())
	}

	def setResourceBundles(resourceBundles: java.util.Set[String]) {
		this.resourceBundles = resourceBundles
	}

	override def getMessage(key: String): String = {
		val locale = LocaleContextHolder.getLocale
		val localeKey = locale.toString + "."
		val entry = cache.getOrElseUpdate(localeKey + key, getMessageFromBundles(locale, key))
		entry
	}

	private def getMessageFromBundles(locale: Locale, key: String): String = {
		resourceBundles.foreach(bundleName =>
			try {
				val message = ResourceBundleHelper.getMessage(locale, bundleName, key, false)
				if (message != null) {
					return message
				}
			}
			catch {
				case e: MissingResourceException => false
			}
		)
		null
	}

}