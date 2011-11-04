// ============================================================================
//
// Copyright (C) 2006-2011 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.repository.services.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.wsdl.Definition;
import javax.wsdl.Operation;
import javax.wsdl.PortType;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.talend.commons.exception.PersistenceException;
import org.talend.commons.exception.SystemException;
import org.talend.commons.ui.runtime.exception.ExceptionHandler;
import org.talend.commons.ui.swt.formtools.LabelledFileField;
import org.talend.core.model.properties.ByteArray;
import org.talend.core.model.properties.PropertiesFactory;
import org.talend.core.model.properties.ReferenceFileItem;
import org.talend.core.model.repository.ERepositoryObjectType;
import org.talend.core.model.repository.RepositoryViewObject;
import org.talend.core.repository.model.ProxyRepositoryFactory;
import org.talend.core.repository.model.ResourceModelUtils;
import org.talend.repository.ProjectManager;
import org.talend.repository.model.IProxyRepositoryFactory;
import org.talend.repository.model.IRepositoryNode.ENodeType;
import org.talend.repository.model.IRepositoryNode.EProperties;
import org.talend.repository.model.RepositoryNode;
import org.talend.repository.services.model.services.ServiceConnection;
import org.talend.repository.services.model.services.ServiceItem;
import org.talend.repository.services.model.services.ServiceOperation;
import org.talend.repository.services.model.services.ServicePort;
import org.talend.repository.services.model.services.ServicesFactory;
import org.talend.repository.services.utils.TemplateProcessor;

/**
 * hwang class global comment. Detailled comment
 */
public class OpenWSDLPage extends WizardPage {

    private RepositoryNode repositoryNode;

    private LabelledFileField wsdlText;

    private String path;

    private boolean createWSDL;

    private ServiceItem item = null;

    private boolean creation = false;

    private IPath pathToSave;

    protected OpenWSDLPage(RepositoryNode repositoryNode, IPath pathToSave, ServiceItem item, String pageName, boolean creation) {
        super(pageName);
        this.creation = creation;
        this.pathToSave = pathToSave;
        this.item = item;
        this.repositoryNode = repositoryNode;
        this.setTitle("Edit WSDL");
        this.setMessage("choose a WSDL file");
    }

    public void createControl(Composite parent) {
        Composite parentArea = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 5;
        parentArea.setLayout(layout);
        final Button check = new Button(parentArea, SWT.CHECK);
        check.setText("create new wsdl file");
        check.setSelection(false);
        createWSDL = false;
        check.addSelectionListener(new SelectionAdapter() {

            public void widgetSelected(SelectionEvent e) {
                if (check.getSelection()) {
                    wsdlText.setVisible(false);
                    createWSDL = true;
                    setPageComplete(true);
                } else {
                    wsdlText.setVisible(true);
                    createWSDL = false;
                    path = wsdlText.getText();
                    if (path.trim().length() > 0) {
                        setPageComplete(true);
                    } else {
                        setPageComplete(false);
                    }
                }
            }

        });
        GridData gridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gridData.horizontalSpan = 5;
        gridData.verticalSpan = 1;
        check.setLayoutData(gridData);

        String[] xmlExtensions = { "*.xml;*.xsd;*.wsdl", "*.*", "*" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        wsdlText = new LabelledFileField(parentArea, "WSDL", //$NON-NLS-1$
                xmlExtensions);
        if (item.getConnection() != null) {
            wsdlText.setText(((ServiceConnection) item.getConnection()).getWSDLPath());
        }
        path = wsdlText.getText();

        if (path.trim().length() > 0) {
            setPageComplete(true);
        } else {
            setPageComplete(false);
        }
        addListener();
        setControl(parentArea);

    }

    private void addListener() {
        wsdlText.addModifyListener(new ModifyListener() {

            public void modifyText(ModifyEvent e) {
                path = wsdlText.getText();
                if (path.trim().length() > 0) {
                    setPageComplete(true);
                } else {
                    setPageComplete(false);
                }
            }

        });
    }

    public boolean finish() {

        String label = item.getProperty().getLabel();
        String version = item.getProperty().getVersion();
        String wsdlFileName = label + "_" + version + ".wsdl";

        IProxyRepositoryFactory factory = ProxyRepositoryFactory.getInstance();
        if (creation) {
            item.setConnection(ServicesFactory.eINSTANCE.createServiceConnection());
            item.getProperty().setId(factory.getNextId());
            try {
                factory.create(item, pathToSave);
            } catch (PersistenceException e) {
                ExceptionHandler.process(e);
            }
        }

        IProject currentProject;
        try {
            currentProject = ResourceModelUtils.getProject(ProjectManager.getInstance().getCurrentProject());
        } catch (PersistenceException e) {
            ExceptionHandler.process(e);
            return false;
        }
        String foldPath = item.getState().getPath();
        String folder = "";
        if (!foldPath.equals("")) {
            folder = "/" + foldPath;
        }
        IFile fileTemp = currentProject.getFolder("services" + folder).getFile(wsdlFileName);

        if (!createWSDL) {
            if (path != null && !path.trim().equals("")) {
                item.setConnection(ServicesFactory.eINSTANCE.createServiceConnection());
                ((ServiceConnection) item.getConnection()).setWSDLPath(path);
                File file = new File(path);
                FileInputStream source;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    source = new FileInputStream(file);
                    try {

                        byte[] buf = new byte[1024];
                        int i = 0;
                        while ((i = source.read(buf)) != -1) {
                            bos.write(buf, 0, i);
                        }
                    } catch (IOException e) {
                        ExceptionHandler.process(e);
                    } finally {
                        try {
                            source.close();
                        } catch (Exception e) {
                        }
                        try {
                            bos.close();
                        } catch (Exception e) {
                        }
                    }
                } catch (FileNotFoundException e2) {
                    ExceptionHandler.process(e2);
                } // copy file to item
                try {
                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
                    if (!fileTemp.exists()) {
                        fileTemp.create(byteArrayInputStream, true, null);
                    } else {
                        fileTemp.setContents(byteArrayInputStream, 0, null);
                    }
                } catch (CoreException e) {
                    ExceptionHandler.process(e);
                }
                //
                if (item.getReferenceResources().size() == 0) {
                    ReferenceFileItem createReferenceFileItem = PropertiesFactory.eINSTANCE.createReferenceFileItem();
                    ByteArray byteArray = PropertiesFactory.eINSTANCE.createByteArray();
                    createReferenceFileItem.setContent(byteArray);
                    createReferenceFileItem.setExtension("wsdl");
                    item.getReferenceResources().add(createReferenceFileItem);
                    createReferenceFileItem.getContent().setInnerContent(bos.toByteArray());
                } else {
                    ((ReferenceFileItem) item.getReferenceResources().get(0)).getContent().setInnerContent(bos.toByteArray());
                }
                //
                populateModelFromWsdl(factory, path, item, repositoryNode);
            }
        } else { // create new wsdl file
            try {
                ((ServiceConnection) item.getConnection()).setWSDLPath("");
                ((ServiceConnection) item.getConnection()).getServicePort().clear();

                Map<String, Object> wsdlInfo = new HashMap<String, Object>();
                wsdlInfo.put("serviceName", label); //$NON-NLS-1$

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                String templatePath = "/resources/wsdl-template.wsdl"; //$NON-NLS-1$
                TemplateProcessor.processTemplate(templatePath, wsdlInfo, new OutputStreamWriter(baos));

                // ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(new byte[0]);
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(baos.toByteArray());
                if (!fileTemp.exists()) {
                    fileTemp.create(byteArrayInputStream, true, null);
                } else {
                    fileTemp.setContents(byteArrayInputStream, 0, null);
                }
            } catch (SystemException e1) {
                ExceptionHandler.process(e1);
            } catch (CoreException e) {
                ExceptionHandler.process(e);
            }
            //
            if (item.getReferenceResources().size() == 0) {
                ReferenceFileItem createReferenceFileItem = PropertiesFactory.eINSTANCE.createReferenceFileItem();
                ByteArray byteArray = PropertiesFactory.eINSTANCE.createByteArray();
                createReferenceFileItem.setContent(byteArray);
                createReferenceFileItem.setExtension("wsdl");
                item.getReferenceResources().add(createReferenceFileItem);
                createReferenceFileItem.getContent().setInnerContent("".getBytes());
            } else {
                ((ReferenceFileItem) item.getReferenceResources().get(0)).getContent().setInnerContent("".getBytes());
            }

            populateModelFromWsdl(factory, fileTemp.getLocation().toPortableString(), item, repositoryNode);
        }

        try {
            factory.save(item);
            ProxyRepositoryFactory.getInstance().saveProject(ProjectManager.getInstance().getCurrentProject());
            return true;
        } catch (PersistenceException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void populateModelFromWsdl(IProxyRepositoryFactory factory, String wsdlPath, ServiceItem serviceItem,
            RepositoryNode serviceRepositoryNode) {

        WSDLFactory wsdlFactory;
        try {
            wsdlFactory = WSDLFactory.newInstance();
            WSDLReader newWSDLReader = wsdlFactory.newWSDLReader();
            newWSDLReader.setExtensionRegistry(wsdlFactory.newPopulatedExtensionRegistry());
            newWSDLReader.setFeature(com.ibm.wsdl.Constants.FEATURE_VERBOSE, false);
            Definition definition = newWSDLReader.readWSDL(wsdlPath);
            Map portTypes = definition.getAllPortTypes();
            Iterator it = portTypes.keySet().iterator();
            serviceRepositoryNode.getChildren().clear();
            ((ServiceConnection) serviceItem.getConnection()).getServicePort().clear();
            while (it.hasNext()) {
                QName key = (QName) it.next();
                PortType portType = (PortType) portTypes.get(key);
                ServicePort port = ServicesFactory.eINSTANCE.createServicePort();
                port.setId(factory.getNextId());
                port.setName(portType.getQName().getLocalPart());
                List<Operation> list = portType.getOperations();
                for (Operation operation : list) {
                    ServiceOperation serviceOperation = ServicesFactory.eINSTANCE.createServiceOperation();
                    serviceOperation.setId(factory.getNextId());
                    RepositoryNode operationNode = new RepositoryNode(new RepositoryViewObject(serviceItem.getProperty()),
                            serviceRepositoryNode, ENodeType.REPOSITORY_ELEMENT);
                    operationNode.setProperties(EProperties.LABEL, serviceItem.getProperty().getLabel());
                    operationNode.setProperties(EProperties.CONTENT_TYPE, ERepositoryObjectType.SERVICESOPERATION);
                    serviceOperation.setName(operation.getName());
                    if (operation.getDocumentationElement() != null) {
                        serviceOperation.setDocumentation(operation.getDocumentationElement().getTextContent());
                    }
                    serviceOperation.setLabel(operation.getName());
                    port.getServiceOperation().add(serviceOperation);
                }
                ((ServiceConnection) serviceItem.getConnection()).getServicePort().add(port);
            }
        } catch (WSDLException e) {
            ExceptionHandler.process(e);
        }

    }

}
