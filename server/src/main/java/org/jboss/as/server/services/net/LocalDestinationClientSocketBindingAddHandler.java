/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.server.services.net;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.network.ClientSocketBinding;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;

import java.net.UnknownHostException;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FIXED_SOURCE_PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_REF;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOURCE_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOURCE_PORT;

/**
 * @author Jaikiran Pai
 */
public class LocalDestinationClientSocketBindingAddHandler extends AbstractAddStepHandler {

    static final LocalDestinationClientSocketBindingAddHandler INSTANCE = new LocalDestinationClientSocketBindingAddHandler();

    public static ModelNode getOperation(final ModelNode address, final ModelNode localDestinationClientSocketBinding) {
        final ModelNode addOperation = new ModelNode();
        addOperation.get(OP).set(ADD);
        addOperation.get(OP_ADDR).set(address);
        // socket binding reference
        addOperation.get(SOCKET_BINDING_REF).set(localDestinationClientSocketBinding.get(SOCKET_BINDING_REF));

        // (optional) source interface
        if (localDestinationClientSocketBinding.get(SOURCE_INTERFACE).isDefined()) {
            addOperation.get(SOURCE_INTERFACE).set(localDestinationClientSocketBinding.get(SOURCE_INTERFACE));
        }
        // (optional) source port
        if (localDestinationClientSocketBinding.get(SOURCE_PORT).isDefined()) {
            addOperation.get(SOURCE_PORT).set(localDestinationClientSocketBinding.get(SOURCE_PORT));
        }
        if (localDestinationClientSocketBinding.get(FIXED_SOURCE_PORT).isDefined()) {
            addOperation.get(FIXED_SOURCE_PORT).set(localDestinationClientSocketBinding.get(FIXED_SOURCE_PORT));
        }
        return addOperation;
    }

    @Override
    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {

        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String clientSocketBindingName = address.getLastElement().getValue();
        model.get(ModelDescriptionConstants.NAME).set(clientSocketBindingName);
        model.get(ModelDescriptionConstants.SOCKET_BINDING_REF).set(operation.get(ModelDescriptionConstants.SOCKET_BINDING_REF));
        if (operation.hasDefined(ModelDescriptionConstants.SOURCE_INTERFACE)) {
            model.get(ModelDescriptionConstants.SOURCE_INTERFACE).set(operation.get(ModelDescriptionConstants.SOURCE_INTERFACE));
        }
        if (operation.hasDefined(ModelDescriptionConstants.SOURCE_PORT)) {
            model.get(ModelDescriptionConstants.SOURCE_PORT).set(operation.get(ModelDescriptionConstants.SOURCE_PORT));
        }
        if (operation.hasDefined(FIXED_SOURCE_PORT)) {
            model.get(ModelDescriptionConstants.FIXED_SOURCE_PORT).set(operation.get(FIXED_SOURCE_PORT));
        }

    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model,
                                  final ServiceVerificationHandler verificationHandler, final List<ServiceController<?>> serviceControllers) throws OperationFailedException {

        final String clientSocketName = model.get(ModelDescriptionConstants.NAME).asString();
        final ServiceController<ClientSocketBinding> clientSocketBindingServiceController;
        try {
            clientSocketBindingServiceController = this.installClientSocketBindingService(context, model, clientSocketName);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        serviceControllers.add(clientSocketBindingServiceController);
    }

    public static ServiceController<ClientSocketBinding> installClientSocketBindingService(final OperationContext context, final ModelNode model,
                                                                                           final String clientSocketName) throws OperationFailedException, UnknownHostException {
        final ServiceTarget serviceTarget = context.getServiceTarget();

        final String socketBindingRef = LocalDestinationSocketBindingResourceDefinition.SOCKET_BINDING_REF.validateResolvedOperation(model).asString();
        // (optional) source interface
        final ModelNode sourceInterfaceModelNode = ClientSocketBindingResourceDefinition.SOURCE_INTERFACE.validateResolvedOperation(model);
        final String sourceInterfaceName = sourceInterfaceModelNode.isDefined() ? sourceInterfaceModelNode.asString() : null;
        // (optional) source port
        final ModelNode sourcePortModelNode = ClientSocketBindingResourceDefinition.SOURCE_PORT.validateResolvedOperation(model);
        final Integer sourcePort = sourcePortModelNode.isDefined() ? sourcePortModelNode.asInt() : null;
        // (optional) fixedSourcePort
        final ModelNode fixedSourcePortModelNode = ClientSocketBindingResourceDefinition.FIXED_SOURCE_PORT.validateResolvedOperation(model);
        final boolean fixedSourcePort = fixedSourcePortModelNode.isDefined() ? fixedSourcePortModelNode.asBoolean() : false;

        // create the service
        final LocalDestinationClientSocketBindingService clientSocketBindingService = new LocalDestinationClientSocketBindingService(clientSocketName, sourcePort, fixedSourcePort);
        final ServiceBuilder<ClientSocketBinding> serviceBuilder = serviceTarget.addService(ClientSocketBinding.CLIENT_SOCKET_BINDING_BASE_SERVICE_NAME.append(clientSocketName), clientSocketBindingService);
        // add dependency on the SocketBinding service corresponding to the socket binding ref
        serviceBuilder.addDependency(SocketBinding.JBOSS_BINDING_NAME.append(socketBindingRef), SocketBinding.class, clientSocketBindingService.getLocalDestinationSocketBindingInjector());
        // if a source interface has been specified then add a dependency on it
        if (sourceInterfaceName != null) {
            serviceBuilder.addDependency(NetworkInterfaceService.JBOSS_NETWORK_INTERFACE.append(sourceInterfaceName), NetworkInterfaceBinding.class, clientSocketBindingService.getSourceNetworkInterfaceBindingInjector());
        }
        // add a dependency on the socket binding manager
        serviceBuilder.addDependency(SocketBindingManager.SOCKET_BINDING_MANAGER, SocketBindingManager.class, clientSocketBindingService.getSocketBindingManagerInjector());
        // install the service
        return serviceBuilder.setInitialMode(ServiceController.Mode.ON_DEMAND).install();
    }
}
