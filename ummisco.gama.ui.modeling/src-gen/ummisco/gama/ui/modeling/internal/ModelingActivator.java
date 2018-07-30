/*
 * generated by Xtext
 */
package ummisco.gama.ui.modeling.internal;

import java.util.Collections;
import java.util.Map;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.xtext.ui.shared.SharedStateModule;
import org.eclipse.xtext.util.Modules2;
import org.osgi.framework.BundleContext;

import com.google.common.collect.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;

import msi.gama.lang.gaml.GamlRuntimeModule;
import msi.gama.lang.gaml.ui.GamlUiModule;

/**
 * This class was generated. Customizations should only happen in a newly introduced subclass.
 */
public class ModelingActivator extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "ummisco.gama.ui.modeling";
	public static final String MSI_GAMA_LANG_GAML_GAML = "msi.gama.lang.gaml.Gaml";

	private static ModelingActivator INSTANCE;

	private final Map<String, Injector> injectors =
			Collections.synchronizedMap(Maps.<String, Injector> newHashMapWithExpectedSize(1));

	@Override
	public void start(final BundleContext context) throws Exception {
		super.start(context);
		INSTANCE = this;
	}

	@Override
	public void stop(final BundleContext context) throws Exception {
		injectors.clear();
		INSTANCE = null;
		super.stop(context);
	}

	public static ModelingActivator getInstance() {
		return INSTANCE;
	}

	public Injector getInjector(final String language) {
		synchronized (injectors) {
			Injector injector = injectors.get(language);
			if (injector == null) {
				injectors.put(language, injector = createInjector(language));
			}
			return injector;
		}
	}

	protected Injector createInjector(final String language) {
		try {
			final com.google.inject.Module runtimeModule = getRuntimeModule(language);
			final com.google.inject.Module sharedStateModule = getSharedStateModule();
			final com.google.inject.Module uiModule = getUiModule(language);
			final com.google.inject.Module mergedModule = Modules2.mixin(runtimeModule, sharedStateModule, uiModule);
			return Guice.createInjector(mergedModule);
		} catch (final Exception e) {
			System.out.println("Failed to create injector for " + language);
			System.err.println(e.getMessage());
			throw new RuntimeException("Failed to create injector for " + language, e);
		}
	}

	protected com.google.inject.Module getRuntimeModule(final String grammar) {
		if (MSI_GAMA_LANG_GAML_GAML.equals(grammar)) { return new GamlRuntimeModule(); }
		throw new IllegalArgumentException(grammar);
	}

	protected com.google.inject.Module getUiModule(final String grammar) {
		if (MSI_GAMA_LANG_GAML_GAML.equals(grammar)) { return new GamlUiModule(this); }
		throw new IllegalArgumentException(grammar);
	}

	protected com.google.inject.Module getSharedStateModule() {
		return new SharedStateModule();
	}

}
