function setup(module) {
    module
        .config(require('./routes'))
        .service(
            'PhysicalFlowStore',
            require('./service/physical-flow-store'))
        .component(
            'waltzPhysicalFlowOverview',
            require('./components/overview/physical-flow-overview'))
        .component(
            'waltzPhysicalFlowTable',
            require('./components/flow-table/flow-table'))
        .component(
            'waltzPhysicalFlowAttributeEditor',
            require('./components/attribute-editor/physical-flow-attribute-editor'));
}


export default setup;
