/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.processor;

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XFormsFeatures {

    private static final ResourceConfig[] stylesheets = {
            // Standard CSS
            new ResourceConfig("/ops/yui/container/assets/skins/sam/container.css", null),
            new ResourceConfig("/ops/yui/progressbar/assets/skins/sam/progressbar.css", null),
            // Calendar CSS
            new ResourceConfig("/ops/javascript/jscalendar/calendar-blue.css", null) {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return "jscalendar".equals(XFormsProperties.getDatePicker(containingDocument));
                }

                protected String getFeatureName() { return "jscalendar"; }
            },
            new ResourceConfig("/ops/yui/calendar/assets/skins/sam/calendar.css", null) {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return !"jscalendar".equals(XFormsProperties.getDatePicker(containingDocument));
                }

                protected String getFeatureName() { return "yuicalendar"; }
            },
            // Yahoo! UI Library
            new ResourceConfig("/ops/yui/treeview/assets/skins/sam/treeview.css", null) {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
                public String getFeatureName() { return "tree"; }
            },
            new ResourceConfig("/ops/yui/examples/treeview/assets/css/check/tree.css", null) {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
                public String getFeatureName() { return "tree"; }
            },
            new ResourceConfig("/ops/yui/menu/assets/skins/sam/menu.css", null) {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isMenuInUse(appearancesMap) || isYUIRTEInUse(containingDocument, appearancesMap);
                }
                private final String[] FEATURE_NAMES = new String[] { "menu", "yuirte" };
                protected String[] getFeatureNames() {
                    return FEATURE_NAMES;
                }
            },
            // HTML area
            // NOTE: This doesn't work, probably because FCK editor files must be loaded in an iframe
//            new ResourceConfig("/ops/fckeditor/editor/skins/default/fck_editor.css", null) {
//                public boolean isInUse(Map appearancesMap) {
//                    return isHtmlAreaInUse(appearancesMap);
//                }
//                public String getFeatureName() { return "htmlarea"; }
//            },
            new ResourceConfig("/ops/yui/editor/assets/skins/sam/editor.css", null) {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isYUIRTEInUse(containingDocument, appearancesMap);
                }
                public String getFeatureName() { return "yuirte"; }
            },
            new ResourceConfig("/ops/yui/button/assets/skins/sam/button.css", null) {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isYUIRTEInUse(containingDocument, appearancesMap);
                }
                public String getFeatureName() { return "yuirte"; }
            },
            // Other standard stylesheets
            new ResourceConfig("/config/theme/xforms.css", null),
            new ResourceConfig("/config/theme/error.css", null)
    };

    private static final ResourceConfig[] scripts = {
            // Calendar scripts
            new ResourceConfig("/ops/javascript/jscalendar/calendar.js", "/ops/javascript/jscalendar/calendar-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return "jscalendar".equals(XFormsProperties.getDatePicker(containingDocument));
                }
                protected String getFeatureName() { return "jscalendar"; }
            },
            new ResourceConfig("/ops/javascript/jscalendar/lang/calendar-en.js", "/ops/javascript/jscalendar/lang/calendar-en-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return "jscalendar".equals(XFormsProperties.getDatePicker(containingDocument));
                }
                protected String getFeatureName() { return "jscalendar"; }
            },
            new ResourceConfig("/ops/javascript/jscalendar/calendar-setup.js", "/ops/javascript/jscalendar/calendar-setup-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return "jscalendar".equals(XFormsProperties.getDatePicker(containingDocument));
                }
                protected String getFeatureName() { return "jscalendar"; }
            },
            // Yahoo UI Library
            new ResourceConfig("/ops/yui/yahoo/yahoo.js", "/ops/yui/yahoo/yahoo-min.js"),
            // Selector is so far only used offline
            new ResourceConfig("/ops/yui/selector/selector.js", "/ops/yui/selector/selector-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return XFormsProperties.isOfflineMode(containingDocument);
                }
                public String getFeatureName() { return "offline"; }
            },
            new ResourceConfig("/ops/yui/event/event.js", "/ops/yui/event/event-min.js"),
            new ResourceConfig("/ops/yui/dom/dom.js", "/ops/yui/dom/dom-min.js"),
            new ResourceConfig("/ops/yui/connection/connection.js", "/ops/yui/connection/connection-min.js"),
            new ResourceConfig("/ops/yui/element/element.js", "/ops/yui/element/element-min.js"),
            new ResourceConfig("/ops/yui/animation/animation.js", "/ops/yui/animation/animation-min.js"),
            new ResourceConfig("/ops/yui/progressbar/progressbar.js", "/ops/yui/progressbar/progressbar-min.js"),
            new ResourceConfig("/ops/yui/dragdrop/dragdrop.js", "/ops/yui/dragdrop/dragdrop-min.js"),
            new ResourceConfig("/ops/yui/container/container.js", "/ops/yui/container/container-min.js"),
            new ResourceConfig("/ops/yui/examples/container/assets/containerariaplugin.js", "/ops/yui/examples/container/assets/containerariaplugin-min.js"),
//            new ResourceConfig("/ops/yui/get/get.js", "/ops/yui/get/get-min.js"),

            new ResourceConfig("/ops/yui/calendar/calendar.js", "/ops/yui/calendar/calendar-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return !"jscalendar".equals(XFormsProperties.getDatePicker(containingDocument));
                }
                protected String getFeatureName() { return "yuicalendar"; }
            },
            new ResourceConfig("/ops/yui/slider/slider.js", "/ops/yui/slider/slider-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isRangeInUse(appearancesMap);
                }
                public String getFeatureName() { return "range"; }
            },
            new ResourceConfig("/ops/yui/treeview/treeview.js", "/ops/yui/treeview/treeview-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
                public String getFeatureName() { return "tree"; }
            },
            new ResourceConfig("/ops/yui/examples/treeview/assets/js/TaskNode.js", "/ops/yui/examples/treeview/assets/js/TaskNode-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isTreeInUse(appearancesMap);
                }
                public String getFeatureName() { return "tree"; }
            },
            new ResourceConfig("/ops/yui/menu/menu.js", "/ops/yui/menu/menu-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isMenuInUse(appearancesMap) || isYUIRTEInUse(containingDocument, appearancesMap);
                }
                private final String[] FEATURE_NAMES = new String[] { "menu", "yuirte" };
                protected String[] getFeatureNames() {
                    return FEATURE_NAMES;
                }
            },
            // HTML area
            new ResourceConfig("/ops/fckeditor/fckeditor.js", null) {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isFCKEditorInUse(containingDocument, appearancesMap);
                }
                public String getFeatureName() { return "fckeditor"; }
            },
            new ResourceConfig("/ops/yui/button/button.js", "/ops/yui/button/button-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isYUIRTEInUse(containingDocument, appearancesMap);
                }
                public String getFeatureName() { return "yuirte"; }
            },
            new ResourceConfig("/ops/yui/editor/editor.js", "/ops/yui/editor/editor-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isYUIRTEInUse(containingDocument, appearancesMap);
                }
                public String getFeatureName() { return "yuirte"; }
            },
            // Autocomplete
            new ResourceConfig("/ops/javascript/suggest-common.js", "/ops/javascript/suggest-common-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isAutocompleteInUse(appearancesMap);
                }
                public String getFeatureName() { return "autocomplete"; }
            },
            new ResourceConfig("/ops/javascript/suggest-actb.js", "/ops/javascript/suggest-actb-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return isAutocompleteInUse(appearancesMap);
                }
                public String getFeatureName() { return "autocomplete"; }
            },
            // ajaxxslt (to compute XPath expressions on the client-side when offline)
            new ResourceConfig("/ops/javascript/ajaxxslt/util.js", "/ops/javascript/ajaxxslt/util-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return XFormsProperties.isOfflineMode(containingDocument);
                }
                public String getFeatureName() { return "offline"; }
            },
            new ResourceConfig("/ops/javascript/ajaxxslt/xmltoken.js", "/ops/javascript/ajaxxslt/xmltoken-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return XFormsProperties.isOfflineMode(containingDocument);
                }
                public String getFeatureName() { return "offline"; }
            },
            new ResourceConfig("/ops/javascript/ajaxxslt/dom.js", "/ops/javascript/ajaxxslt/dom-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return XFormsProperties.isOfflineMode(containingDocument);
                }
                public String getFeatureName() { return "offline"; }
            },
            new ResourceConfig("/ops/javascript/ajaxxslt/xpath.js", "/ops/javascript/ajaxxslt/xpath-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return XFormsProperties.isOfflineMode(containingDocument);
                }
                public String getFeatureName() { return "offline"; }
            },
            // Encrytion library to encrypt data stored in the Gears store
            new ResourceConfig("/ops/javascript/encryption/encryption.js", "/ops/javascript/encryption/encryption-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return XFormsProperties.isOfflineMode(containingDocument);
                }
                public String getFeatureName() { return "offline"; }
            },
            new ResourceConfig("/ops/javascript/encryption/md5.js", "/ops/javascript/encryption/md5-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return XFormsProperties.isOfflineMode(containingDocument);
                }
                public String getFeatureName() { return "offline"; }
            },

            new ResourceConfig("/ops/javascript/encryption/utf-8.js", "/ops/javascript/encryption/utf-8-min.js") {
                public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
                    return XFormsProperties.isOfflineMode(containingDocument);
                }
                public String getFeatureName() { return "offline"; }
            },
            // Underscore library
            new ResourceConfig("/ops/javascript/underscore/underscore.js", "/ops/javascript/underscore/underscore-min.js"),
            // XForms client
            new ResourceConfig("/ops/javascript/xforms.js",                                     "/ops/javascript/xforms-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/util/ExecutionQueue.js",                 "/ops/javascript/orbeon/util/ExecutionQueue-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/server/Server.js",                "/ops/javascript/orbeon/xforms/server/Server-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/server/AjaxServer.js",            "/ops/javascript/orbeon/xforms/server/AjaxServer-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/server/UploadServer.js",          "/ops/javascript/orbeon/xforms/server/UploadServer-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/Form.js",                         "/ops/javascript/orbeon/xforms/Form-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/Page.js",                         "/ops/javascript/orbeon/xforms/Page-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/Control.js",              "/ops/javascript/orbeon/xforms/control/Control-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/CalendarResources.js",    "/ops/javascript/orbeon/xforms/control/CalendarResources-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/Calendar.js",             "/ops/javascript/orbeon/xforms/control/Calendar-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/Upload.js",               "/ops/javascript/orbeon/xforms/control/Upload-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/RTEConfig.js",            "/ops/javascript/orbeon/xforms/control/RTEConfig-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/RTE.js",                  "/ops/javascript/orbeon/xforms/control/RTE-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/control/Tree.js",                 "/ops/javascript/orbeon/xforms/control/Tree-min.js"),
            new ResourceConfig("/ops/javascript/orbeon/xforms/action/Message.js",               "/ops/javascript/orbeon/xforms/action/Message-min.js")
    };

    public static class ResourceConfig {
        private String fullResource;
        private String minResource;

        public ResourceConfig(String fullResource, String minResource) {
            this.fullResource = fullResource;
            this.minResource = minResource;
        }

        public String getResourcePath(boolean tryMinimal) {
            // Load minimal resource if requested and there exists a minimal resource
            return (tryMinimal && minResource != null) ? minResource : fullResource;
        }

        public boolean isInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
            // Default to true but can be overridden
            return true;
        }

        public static boolean isInUse(Map appearancesMap, String controlName) {
            final Map controlMap = (Map) appearancesMap.get(controlName);
            return controlMap != null;
        }

        public static boolean isInUse(Map appearancesMap, String controlName, String appearanceOrMediatypeName) {
            final Map controlMap = (Map) appearancesMap.get(controlName);
            if (controlMap == null) return false;
            final Object controlAppearanceOrMediatypeList = controlMap.get(appearanceOrMediatypeName);
            return controlAppearanceOrMediatypeList != null;
        }


        protected boolean isRangeInUse(Map appearancesMap) {
            return isInUse(appearancesMap, "range");
        }

        protected boolean isTreeInUse(Map appearancesMap) {
            return isInUse(appearancesMap, "select1", XFormsSelect1Control.TREE_APPEARANCE) || isInUse(appearancesMap, "select", XFormsSelect1Control.TREE_APPEARANCE);
        }

        protected boolean isMenuInUse(Map appearancesMap) {
            return isInUse(appearancesMap, "select1", XFormsSelect1Control.MENU_APPEARANCE) || isInUse(appearancesMap, "select", XFormsSelect1Control.MENU_APPEARANCE);
        }

        protected boolean isAutocompleteInUse(Map appearancesMap) {
            return isInUse(appearancesMap, "select1", XFormsSelect1Control.AUTOCOMPLETE_APPEARANCE);
        }

        private boolean isHtmlAreaInUse(Map appearancesMap) {
            return isInUse(appearancesMap, "textarea", "text/html");
        }

        protected boolean isFCKEditorInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
            return isHtmlAreaInUse(appearancesMap) && !"yui".equals(XFormsProperties.getHTMLEditor(containingDocument));
        }

        protected boolean isYUIRTEInUse(XFormsContainingDocument containingDocument, Map appearancesMap) {
            return isHtmlAreaInUse(appearancesMap) && "yui".equals(XFormsProperties.getHTMLEditor(containingDocument));
        }
    }

    public static List<ResourceConfig> getCSSResources(XFormsContainingDocument containingDocument, Map appearancesMap) {
        final List<ResourceConfig> result = new ArrayList<ResourceConfig>();
        for (final ResourceConfig resourceConfig: stylesheets) {
            if (resourceConfig.isInUse(containingDocument, appearancesMap)) {
                // Only include stylesheet if needed
                result.add(resourceConfig);
            }
        }
        return result;
    }

    public static List<ResourceConfig> getJavaScriptResources(XFormsContainingDocument containingDocument, Map appearancesMap) {
        final List<ResourceConfig> result = new ArrayList<ResourceConfig>();
        for (final ResourceConfig resourceConfig: scripts) {
            if (resourceConfig.isInUse(containingDocument, appearancesMap)) {
                // Only include script if needed
                result.add(resourceConfig);
            }
        }
        return result;
    }
}
