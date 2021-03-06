/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.test;

import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.BootLaunchUIModel;
import org.springframework.ide.eclipse.boot.launch.IProfileHistory;
import org.springframework.ide.eclipse.boot.launch.LaunchTabSelectionModel;
import org.springframework.ide.eclipse.boot.launch.MainTypeNameLaunchTabModel;
import org.springframework.ide.eclipse.boot.launch.ProfileLaunchTabModel;
import org.springframework.ide.eclipse.boot.launch.SelectProjectLaunchTabModel;
import org.springframework.ide.eclipse.boot.launch.livebean.EnableLiveBeanSupportModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;

/**
 * @author Kris De Volder
 */
public class BootLaunchUIModelTest extends BootLaunchTestCase {

	private static final String[] NO_PROFILES =  new String[0];

	public class TestProfileHistory implements IProfileHistory {

		private Map<String, String[]> map = new HashMap<String, String[]>();

		public String[] getHistory(IProject project) {
			String[] h = map.get(project.getName());
			if (h!=null) {
				return h;
			}
			return NO_PROFILES;
		}

		public void setHistory(IProject p, String... history) {
			map.put(p.getName(), history);
		}

	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	protected BootLaunchUIModel model;
	protected TestProfileHistory profileHistory;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		profileHistory = new TestProfileHistory();
		model = new BootLaunchUIModel(profileHistory);
	}

	///// project ////////////////////////////////////////////////////////////

	public void testProjectValidator() throws Exception {
		createPredefinedProject("empty-boot-project");
		createGeneralProject("general");
		assertError("No project selected", model.project.validator);

		model.project.selection.setValue(getProject("non-existant"));
		assertError("does not exist", model.project.validator);

		model.project.selection.setValue(getProject("general"));
		assertError("does not look like a Boot project", model.project.validator);

		model.project.selection.setValue(getProject("empty-boot-project"));
		assertOk(model.project.validator);

		getProject("empty-boot-project").close(new NullProgressMonitor());
		model.project.validator.refresh(); //manual refresh is needed
											// no auto refresh when closing project. This is normal
											// and it is okay since user can't open/close projects
											// while using launch config dialog.
		assertError("is closed", model.project.validator);
	}

	private ILaunchConfigurationWorkingCopy createWorkingCopy() throws CoreException {
		return createWorkingCopy(BootLaunchConfigurationDelegate.LAUNCH_CONFIG_TYPE_ID);
	}

	public void testProjectInitializeFrom() throws Exception {
		IProject fooProject = getProject("foo");

		SelectProjectLaunchTabModel project = model.project;
		LiveVariable<Boolean> dirtyState = model.project.getDirtyState();

		dirtyState.setValue(false);
		project.selection.setValue(fooProject);
		assertTrue(dirtyState.getValue());

		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		project.initializeFrom(wc);
		assertEquals(null, project.selection.getValue());
		assertFalse(dirtyState.getValue());

		BootLaunchConfigurationDelegate.setProject(wc, fooProject);
		project.initializeFrom(wc);
		assertEquals(fooProject, project.selection.getValue());
	}

	public void testProjectPerformApply() throws Exception {
		IProject fooProject = getProject("foo");

		SelectProjectLaunchTabModel project = model.project;
		LiveVariable<Boolean> dirtyState = model.project.getDirtyState();

		dirtyState.setValue(false);
		project.selection.setValue(fooProject);
		assertTrue(dirtyState.getValue());

		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		assertEquals(null, BootLaunchConfigurationDelegate.getProject(wc));

		project.performApply(wc);

		assertFalse(dirtyState.getValue());
		assertEquals(fooProject, BootLaunchConfigurationDelegate.getProject(wc));
	}

	public void testProjectSetDefaults() throws Exception {
		IProject fooProject = getProject("foo");

		SelectProjectLaunchTabModel project = model.project;

		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		BootLaunchConfigurationDelegate.setProject(wc, fooProject);
		assertEquals(fooProject, BootLaunchConfigurationDelegate.getProject(wc));
		project.setDefaults(wc);
		assertEquals(null, BootLaunchConfigurationDelegate.getProject(wc));
	}

	public void testProjectDirtyState() throws Exception {
		SelectProjectLaunchTabModel project = model.project;
		LiveVariable<Boolean> dirtyState = model.project.getDirtyState();

		dirtyState.setValue(false);
		project.selection.setValue(getProject("nono"));
		assertTrue(dirtyState.getValue());
	}

	////// main type //////////////////////////////////////////////////////////

	public void testMainTypeValidator() throws Exception {
		assertEquals("", model.mainTypeName.selection.getValue());
		assertError("No Main type selected", model.mainTypeName.validator);
		model.mainTypeName.selection.setValue("something");
		assertOk(model.mainTypeName.validator);
	}

	public void testMainTypeInitializeFrom() throws Exception {
		MainTypeNameLaunchTabModel mainTypeName = model.mainTypeName;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		LiveVariable<Boolean> dirtyState = model.mainTypeName.getDirtyState();

		BootLaunchConfigurationDelegate.setMainType(wc, "Snuggem");
		dirtyState.setValue(true);

		mainTypeName.initializeFrom(wc);

		assertFalse(dirtyState.getValue());
		assertEquals("Snuggem", mainTypeName.selection.getValue());
	}

	public void testMainTypePerformApply() throws Exception {
		MainTypeNameLaunchTabModel mainTypeName = model.mainTypeName;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		LiveVariable<Boolean> dirtyState = model.mainTypeName.getDirtyState();

		mainTypeName.selection.setValue("Koko");
		assertTrue(dirtyState.getValue());

		mainTypeName.performApply(wc);

		assertEquals("Koko", getMainTypeName(wc));
		assertFalse(dirtyState.getValue());
	}

	public void testMainTypeSetDefaults() throws Exception {
		MainTypeNameLaunchTabModel mainTypeName = model.mainTypeName;

		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		mainTypeName.setDefaults(wc);
		assertEquals("", getMainTypeName(wc));
	}

	protected String getMainTypeName(ILaunchConfigurationWorkingCopy wc)
			throws CoreException {
		return wc.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, (String)null);
	}

	public void testMainTypeDirtyState() throws Exception {
		MainTypeNameLaunchTabModel mainTypeName = model.mainTypeName;
		LiveVariable<Boolean> dirtyState = model.mainTypeName.getDirtyState();

		dirtyState.setValue(false);
		mainTypeName.selection.setValue("something");
		assertTrue(dirtyState.getValue());
	}

	////// profile ////////////////////////////////////////////////////////////////////

	public void testProfileValidator() throws Exception {
		assertEquals(Validator.OK, model.profile.validator);
		//not much to test here, we don't validate profiles at all.
	}

	public void testProfileSetDefaults() throws Exception {
		ProfileLaunchTabModel profile = model.profile;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		profile.setDefaults(wc);
		assertEquals("", BootLaunchConfigurationDelegate.getProfile(wc));
	}

	public void testProfileInitializeFrom() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		ProfileLaunchTabModel profile = model.profile;
		LiveVariable<Boolean> dirty = profile.getDirtyState();

		dirty.setValue(true);
		BootLaunchConfigurationDelegate.setProfile(wc,"some-profile");
		profile.initializeFrom(wc);
		assertFalse(dirty.getValue());
		assertEquals("some-profile", BootLaunchConfigurationDelegate.getProfile(wc));
	}

	public void testProfilePerformApply() throws Exception {
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		ProfileLaunchTabModel profile = model.profile;
		LiveVariable<Boolean> dirty = profile.getDirtyState();

		profile.selection.setValue("some-other-profile");
		assertTrue(dirty.getValue());

		profile.performApply(wc);

		assertFalse(dirty.getValue());
		assertEquals("some-other-profile", BootLaunchConfigurationDelegate.getProfile(wc));
	}

	public void testProfileDirtyState() throws Exception {
		ProfileLaunchTabModel profile = model.profile;
		LiveVariable<Boolean> dirty = profile.getDirtyState();
		dirty.setValue(false);

		profile.selection.setValue("olla-polla");

		assertTrue(dirty.getValue());
	}

	public void testProfilePulldownOptions() throws Exception {
		IProject bootProject = createPredefinedProject("empty-boot-project");
		IProject generalProject = createGeneralProject("general");

		LiveVariable<IProject> project = model.project.selection;
		ProfileLaunchTabModel profile = model.profile;

		assertPulldown(profile /*empty*/);

		createEmptyFile(bootProject, "src/main/resources/application-foo.properties");
		createEmptyFile(bootProject, "src/main/resources/application-bar.properties");

		project.setValue(bootProject);
		assertPulldown(profile, "foo", "bar");

		project.setValue(getProject("invalid"));
		assertPulldown(profile /*empty*/);

		profileHistory.setHistory(generalProject, "something", "borker");
		project.setValue(generalProject);
		assertPulldown(profile, "something", "borker");

		profileHistory.setHistory(bootProject, "old", "older");
		project.setValue(bootProject);
		assertPulldown(profile, "foo", "bar", "old", "older");

		profileHistory.setHistory(bootProject, "new", "newer", "foo");
		profile.profileOptions().refresh(); // See [*] below
		assertPulldown(profile, "foo", "bar", "new", "newer"); //only a single 'foo'!

		// [*] Changing only the history doesn't trigger pull-down options to recompute.
		//This is fine since its not possible to change the history while launch dialog is open.
		//However, it means test code must force a refresh in cases where only the history
		//changed.
	}

	///// EnableDebugSection///////////////////////////////////////////////////////

	public void testDebugValidator() throws Exception {
		assertEquals(Validator.OK, model.enableDebug.validator);
	}

	public void testDebugSetDefaults() throws Exception {
		LaunchTabSelectionModel<Boolean> enableDebug = model.enableDebug;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		enableDebug.setDefaults(wc);
		assertEquals(false, BootLaunchConfigurationDelegate.getEnableDebugOutput(wc));
	}

	public void testDebugInitializeFrom() throws Exception {
		LaunchTabSelectionModel<Boolean> enableDebug = model.enableDebug;
		LiveVariable<Boolean> dirty = enableDebug.getDirtyState();
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();

		BootLaunchConfigurationDelegate.setEnableDebugOutput(wc, true);
		dirty.setValue(true);

		enableDebug.initializeFrom(wc);
		assertFalse(dirty.getValue());
		assertTrue(enableDebug.selection.getValue());

		dirty.setValue(true);
		BootLaunchConfigurationDelegate.setEnableDebugOutput(wc, false);

		enableDebug.initializeFrom(wc);

		assertFalse(dirty.getValue());
		assertFalse(enableDebug.selection.getValue());
	}

	public void testDebugPerformApply() throws Exception {
		LaunchTabSelectionModel<Boolean> enableDebug = model.enableDebug;
		LiveVariable<Boolean> dirty = enableDebug.getDirtyState();
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();

		enableDebug.selection.setValue(true);
		assertTrue(dirty.getValue());

		enableDebug.performApply(wc);

		assertFalse(dirty.getValue());
		assertTrue(BootLaunchConfigurationDelegate.getEnableDebugOutput(wc));

		enableDebug.selection.setValue(false);
		assertTrue(dirty.getValue());

		enableDebug.performApply(wc);

		assertFalse(dirty.getValue());
		assertFalse(BootLaunchConfigurationDelegate.getEnableDebugOutput(wc));
	}

	public void testDebugDirtyState() throws Exception {
		LaunchTabSelectionModel<Boolean> enableDebug = model.enableDebug;
		LiveVariable<Boolean> dirty = enableDebug.getDirtyState();
		dirty.setValue(false);

		enableDebug.selection.setValue(true);

		assertTrue(dirty.getValue());
	}

	///// EnableLiveBeanSupportSection/////////////////////////////////////////////

	public void testLiveBeanValidator() throws Exception {
		LiveVariable<Boolean> enabled = model.enableLiveBean.enabled;
		LiveVariable<String> port = model.enableLiveBean.port;
		LiveExpression<ValidationResult> validator = model.enableLiveBean.getValidator();

		enabled.setValue(true);
		port.setValue("8888");
		assertOk(validator);

		port.setValue("Unparseable");
		assertError("can't be parsed as an Integer", validator);

		enabled.setValue(false);
		assertOk(validator); //if disabled we shouldn't check the port as it doesn't matter.

		port.setValue("10000000");
		enabled.setValue(true);
		assertError("should be smaller than", validator);

		port.setValue("-111");
		assertError("should be a positive", validator);

		port.setValue("  8888   ");
		assertOk(validator); // tolerate spaces

		port.setValue(null); //
		assertError("must be specified", validator);

		port.setValue("   "); //
		assertError("must be specified", validator);

		port.setValue("");
		assertError("must be specified", validator);

		port.setValue("8888");
		assertOk(validator);
	}

	public void testLiveBeanSetDefaults() throws Exception {
		EnableLiveBeanSupportModel elbModel = this.model.enableLiveBean;

		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		elbModel.setDefaults(wc);
		assertTrue(BootLaunchConfigurationDelegate.getEnableLiveBeanSupport(wc));
		int randomPort = Integer.parseInt(BootLaunchConfigurationDelegate.getJMXPort(wc));
		assertTrue(1000 <= randomPort && randomPort <= 65535);
	}

	public void testLiveBeanInitializeFrom() throws Exception {
		EnableLiveBeanSupportModel elbModel = this.model.enableLiveBean;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		LiveVariable<Boolean> enabled = model.enableLiveBean.enabled;
		LiveVariable<String> port = model.enableLiveBean.port;
		LiveVariable<Boolean> dirty = elbModel.getDirtyState();

		BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc, true);
		BootLaunchConfigurationDelegate.setJMXPort(wc, "3456");
		dirty.setValue(true);

		elbModel.initializeFrom(wc);

		assertFalse(dirty.getValue());
		assertEquals("3456", port.getValue());
		assertTrue(enabled.getValue());

		BootLaunchConfigurationDelegate.setEnableLiveBeanSupport(wc, false);
		elbModel.initializeFrom(wc);
		assertFalse(enabled.getValue());
	}

	public void testLiveBeanPerformApply() throws Exception {
		EnableLiveBeanSupportModel elbModel = this.model.enableLiveBean;
		ILaunchConfigurationWorkingCopy wc = createWorkingCopy();
		LiveVariable<Boolean> enabled = model.enableLiveBean.enabled;
		LiveVariable<String> port = model.enableLiveBean.port;
		LiveVariable<Boolean> dirty = elbModel.getDirtyState();

		port.setValue("1234");
		enabled.setValue(true);
		assertTrue(dirty.getValue());

		elbModel.performApply(wc);

		assertFalse(dirty.getValue());
		assertTrue(BootLaunchConfigurationDelegate.getEnableLiveBeanSupport(wc));
		assertEquals("1234", BootLaunchConfigurationDelegate.getJMXPort(wc));

		enabled.setValue(false);
		elbModel.performApply(wc);
		assertFalse(BootLaunchConfigurationDelegate.getEnableLiveBeanSupport(wc));

		port.setValue("   8888   ");
		elbModel.performApply(wc);
		assertEquals("8888", BootLaunchConfigurationDelegate.getJMXPort(wc));
	}

	public void testLiveBeanDirtyState() throws Exception {
		EnableLiveBeanSupportModel elbModel = this.model.enableLiveBean;
		LiveVariable<Boolean> enabled = model.enableLiveBean.enabled;
		LiveVariable<String> port = model.enableLiveBean.port;
		LiveVariable<Boolean> dirty = elbModel.getDirtyState();

		dirty.setValue(false);
		port.setValue("something");
		assertTrue(dirty.getValue());

		dirty.setValue(false);
		enabled.setValue(!enabled.getValue());
		assertTrue(dirty.getValue());
	}

	// TODO:
	//   dirtyState

	///// PropertiesTableSection ??? can't be tested in its current form (no separate 'model' to test)


	/**
	 * Verify contents of 'pulldown' menu. This ignores the order of the elements
	 * because discovered elements may come in different orders... but does not
	 * ignore when there are duplicates.
	 */
	private void assertPulldown(ProfileLaunchTabModel profile, String... expecteds) {
		Arrays.sort(expecteds);
		String [] actuals = profile.profileOptions().getValue();
		actuals = Arrays.copyOf(actuals, actuals.length);
		Arrays.sort(actuals);
		assertArrayEquals(expecteds, actuals);
	}

}
