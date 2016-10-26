import angular from "angular";
import {buildHierarchies, initialiseData} from "../common";


const initialState = {
    dataTypes: [],
    trees: []
};


function controller($state,
                    dataTypes,
                    staticPanelStore,
                    svgStore,
                    physicalFlowStore,
                    specificationStore) {

    const vm = initialiseData(this, initialState);

    vm.trees = buildHierarchies(dataTypes);

    vm.nodeSelected = (node) => vm.selectedNode = node;

    svgStore
        .findByKind('DATA_TYPE')
        .then(xs => vm.diagrams = xs);

    staticPanelStore
        .findByGroup("HOME.DATA-TYPE")
        .then(panels => vm.panels = panels);

    vm.blockProcessor = b => {
        b.block.onclick = () => $state.go('main.data-type.code', { code: b.value });
        angular.element(b.block).addClass('clickable');
    };

    physicalFlowStore
        .findForDescribedLineage()
        .then(flows => vm.lineageFlows = flows);

    specificationStore
        .findByDescribedLineage()
        .then(specs => vm.lineageSpecs = specs);

}


controller.$inject = [
    '$state',
    'dataTypes',
    'StaticPanelStore',
    'SvgDiagramStore',
    'PhysicalFlowStore',
    'PhysicalSpecificationStore'
];


const view = {
    template: require('./data-type-list.html'),
    controllerAs: 'ctrl',
    controller
};


export default view;