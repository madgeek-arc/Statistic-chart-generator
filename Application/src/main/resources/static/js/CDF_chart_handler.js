//This variable holds the Library state across the execution of the Chart Formation
var libraryType;
var port = "8080";
var domainName = "localhost";
var protocol = "http";

var domainLink = protocol + "://" + domainName + ":" + port;
var userLocale = navigator.language || 'en-US';

const DEBUGMODE = true;

function fetchChart(jsonData) {
    $.getJSON(jsonData, handleAdminSideData)
        .done()
        .fail(() => {
            console.log("Failed");
        })
        .always();
}

/**
 * Formats numeric values given as string, otherwise returns original data.
 *
 * @param data
 * @returns {*|string}
 */
function formatIfNumeric(data) {
    let num = parseFloat(data);
    const smallIntRegex = "[-+]?\d{1,4}$";
    if (isNaN(num))
        return data;
    else if (num.match(smallIntRegex))
        return num;
    else
        return new Intl.NumberFormat(userLocale).format(num);
}

//Function for loading(= appending to the head) a JS file
function loadJS(url, afterLoadCallback) {

    var fileref = document.createElement('script');
    var firstHeadTaginDOM = document.getElementsByTagName('head')[0];
    var callback = () => {
        if (DEBUGMODE)
            console.log("Library " + document.getElementsByTagName('head')[0].lastChild.getAttribute("src") + " about to be loaded!");
        afterLoadCallback();
    };

    fileref.onload = callback;
    fileref.onreadystatechange = callback;
    fileref.setAttribute("type", "text/javascript");
    fileref.setAttribute("src", url);

    firstHeadTaginDOM.appendChild(fileref);
}

function getCompatibleGoogleChartsType(originalChartType) {
    if (originalChartType === 'treemap')
        return 'TreeMap';

    return originalChartType;
}

//Callback that handles the data sent from the Admin part of the app
function handleAdminSideData(dataJSONobj) {
    // dataJSONobj holds the option-ready version of the JSON that will be passed to the Chart library,
    // along with the queries that must be passed to ChartDataFormatter and eventually to DBAccess
    if (DEBUGMODE)
        console.log("Admin data JSON", dataJSONobj);

    switch (dataJSONobj.library) {
        case "GoogleCharts": {
            loadJS("https://www.gstatic.com/charts/loader.js",
                function () {
                    google.charts.load('current', {packages: ['corechart', 'treemap']});
                    google.charts.setOnLoadCallback(function () {
                        libraryType = dataJSONobj.library;

                        var RequestInfoObj = new Object();
                        //Pass the Chart library to ChartDataFormatter
                        RequestInfoObj.library = dataJSONobj.library;
                        RequestInfoObj.orderBy = dataJSONobj.orderBy;

                        //Create ChartInfo Object Array
                        RequestInfoObj.chartsInfo = [];
                        //Create ChartInfo and pass the Chart data queries to ChartDataFormatter
                        //along with the requested Chart type
                        RequestInfoObj.chartsInfo = dataJSONobj.chartDescription.queriesInfo;

                        passToChartDataFormatter(dataJSONobj, RequestInfoObj,
                            domainLink + "/chart");
                    });
                });
            break;
        }
        case "eCharts": {
            // loadJS("https://cdnjs.cloudflare.com/ajax/libs/echarts/5.2.2/echarts.min.js",
            loadJS("https://cdnjs.cloudflare.com/ajax/libs/echarts/5.2.2/echarts.js",
                function () {

                    //Hold the Library state
                    libraryType = dataJSONobj.library;
                    console.log("dataJSONobj: ", dataJSONobj);
                    console.log("LibraryType: ", libraryType);

                    var RequestInfoObj = new Object();
                    //Pass the Chart library to ChartDataFormatter
                    RequestInfoObj.library = dataJSONobj.library;
                    RequestInfoObj.orderBy = dataJSONobj.orderBy;

                    //Create ChartInfo Object Array
                    RequestInfoObj.chartsInfo = [];

                    //Create ChartInfo and pass the Chart data queries to ChartDataFormatter
                    //along with the requested Chart type
                    for (let index = 0; index < dataJSONobj.chartDescription.queries.length; index++) {

                        var element = dataJSONobj.chartDescription.queries[index];
                        var ChartInfoObj = new Object();

                        if (element.type === undefined)
                            ChartInfoObj.type = dataJSONobj.chartDescription.series[0].type;
                        else
                            ChartInfoObj.type = element.type;

                        if (element.name === undefined)
                            ChartInfoObj.name = null;
                        else
                            ChartInfoObj.name = element.name;

                        ChartInfoObj.query = element.query;
                        RequestInfoObj.chartsInfo.push(ChartInfoObj);
                    }

                    passToChartDataFormatter(dataJSONobj, RequestInfoObj, domainLink + "/chart");
                });
            break;
        }
        case "HighCharts": {

            //Dynamically add JS library
            loadJS("//code.highcharts.com/highcharts.js",
                () => loadJS("//code.highcharts.com/highcharts-more.js",
                    () => loadJS("//code.highcharts.com/modules/heatmap.js",
                        () => loadJS("//code.highcharts.com/modules/treemap.js",
                            () => loadJS("//code.highcharts.com/modules/sankey.js",
                                () => loadJS("//code.highcharts.com/modules/dependency-wheel.js",
                                    () => loadJS("//code.highcharts.com/modules/drilldown.js",
                                        () => loadJS("//code.highcharts.com/modules/no-data-to-display.js",
                                            () => loadJS("//code.highcharts.com/highcharts-3d.js",
                                                () => loadJS("//code.highcharts.com/modules/exporting.js",
                                                    () => loadJS("//code.highcharts.com/modules/offline-exporting.js",
                                                        () => loadJS("//code.highcharts.com/modules/export-data.js",

                                                            function(){
                                                                //Hold the Library state
                                                                libraryType = dataJSONobj.library;

                                                                var RequestInfoObj = new Object();
                                                                //Pass the Chart library to ChartDataFormatter
                                                                RequestInfoObj.library = dataJSONobj.library;
                                                                RequestInfoObj.orderBy = dataJSONobj.orderBy;
                                                                RequestInfoObj.drilldown = dataJSONobj.drilldown;
                                                                //Pass the Chart type to ChartDataFormatter
                                                                var defaultType = dataJSONobj.chartDescription.chart.type;
                                                                //Create ChartInfo Object Array
                                                                RequestInfoObj.chartsInfo = [];

                                                                //Create ChartInfo and pass the Chart data queries to ChartDataFormatter
                                                                //along with the requested Chart type
                                                                dataJSONobj.chartDescription.queries.
                                                                forEach(element => {
                                                                    var ChartInfoObj = new Object();

                                                                    if(element.type === undefined)
                                                                        ChartInfoObj.type = defaultType;
                                                                    else
                                                                        ChartInfoObj.type = element.type;

                                                                    if(element.name === undefined)
                                                                        ChartInfoObj.name = null;
                                                                    else
                                                                        ChartInfoObj.name = element.name;

                                                                    ChartInfoObj.query = element.query;
                                                                    RequestInfoObj.chartsInfo.push(ChartInfoObj);
                                                                });

                                                                passToChartDataFormatter(dataJSONobj,RequestInfoObj,
                                                                    domainLink+"/chart");
                                                            }

                                                        ))))))))))));

            break;
        }
        case "HighMaps":
        {
            loadJS("//code.highcharts.com/maps/highmaps.js",
                () => loadJS("//code.highcharts.com/modules/exporting.js",
                    () => loadJS("//code.highcharts.com/modules/offline-exporting.js",
                        () => loadJS("//code.highcharts.com/modules/export-data.js",
                            function () {
                                console.log("Incoming JSON",dataJSONobj);

                                //Hold the Library state
                                libraryType = dataJSONobj.library;

                                var RequestInfoObj = new Object();
                                //Pass the Chart library to ChartDataFormatter
                                RequestInfoObj.library = dataJSONobj.library;
                                //Create ChartInfo Object Array
                                RequestInfoObj.chartsInfo = [];

                                //Create ChartInfo and pass the Chart data queries to ChartDataFormatter
                                dataJSONobj.mapDescription.queries.
                                forEach(element => {
                                    var ChartInfoObj = new Object();

                                    if(element.name === undefined)
                                        ChartInfoObj.name = null;
                                    else
                                        ChartInfoObj.name = element.name;

                                    ChartInfoObj.query = element.query;
                                    RequestInfoObj.chartsInfo.push(ChartInfoObj);
                                });
                                passToChartDataFormatter(dataJSONobj,RequestInfoObj,
                                    domainLink+"/chart");
                            }))));

            break;
        }
        default:
            if(DEBUGMODE)
                console.log("Unsupported Library: "+ dataJSONobj.library);
    }
}

//Post the admin-side json to the ChartDataFormatter
function passToChartDataFormatter(dataJSONobj, ChartDataFormatterReadyJSONobj, ChartDataFormatterUrl) {
    if (DEBUGMODE) {
        console.log("Passing to CDF: ", ChartDataFormatterReadyJSONobj);
    }

    $.ajax(
        {
            url: this.ChartDataFormatterUrl,
            type: "POST",
            dataType: "json",
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(ChartDataFormatterReadyJSONobj),
            cache: false,
            success: function (data) {
                handleChartDataFormatterResponse(data, dataJSONobj, ChartDataFormatterReadyJSONobj)
            },
            error: function (jqXHR, textStatus, errorThrown) {

                $('#loader').hide();
                if (jqXHR.status === 0) {
                    $('#errorSpan').show().html('Not connected.\nPlease verify your network connection.');
                } else if (jqXHR.status == 404) {
                    $('#errorSpan').show().html('The requested page not found.\nPlease try again or <a href="https://www.openaire.eu/support/helpdesk">contact us</a>, if the problem persists.');
                } else if (jqXHR.status == 500) {
                    $('#errorSpan').show().html('Internal Server Error.\nPlease try again or <a href="https://www.openaire.eu/support/helpdesk">contact us</a>, if the problem persists.');
                } else if (jqXHR.status == 422) {
                    $('#errorSpan').show().html('Unable to fetch data from the database.\nPlease try again or <a href="https://www.openaire.eu/support/helpdesk">contact us</a>, if the problem persists.');
                } else if (jqXHR.status == 504) {
                    $('#errorSpan').show().html('Server took unusually too long to respond.\nPlease try again or <a href="https://www.openaire.eu/support/helpdesk">contact us</a>, if the problem persists.');
                } else {
                    $('#errorSpan').show().html('An unexpected error has occurred [' + jqXHR.status + '].\nPlease try again or <a href="https://www.openaire.eu/support/helpdesk">contact us</a>, if the problem persists.');
                }

            }
        }).done()
        .fail()
        .always();
}

function handleChartDataFormatterResponse(responseData, originalDataJSONobj, ChartDataFormatterReadyJSONobj) {
    if (DEBUGMODE) console.log("Got from CDF: ", responseData);

    if (libraryType !== "GoogleCharts") { // GoogleCharts does not respond with the following transformations.
        // If x-axis labels are numeric, format them using user locale
        responseData.xAxis_categories = responseData.xAxis_categories.map(x => formatIfNumeric(x))
        responseData.dataSeriesNames = responseData.dataSeriesNames.map(x => formatIfNumeric(x))
    }

    //Hide children elements of container
    $("#container").children().remove();

    switch (libraryType) {
        case "HighCharts": {
            var chartJson = convertToValidHighchartJson(responseData, originalDataJSONobj);

            Highcharts.setOptions({
                lang: {
                    drillUpText: '<< Back',
                    decimalPoint: (1.1).toLocaleString(userLocale).charAt(1),
                    thousandsSep: (1000).toLocaleString(userLocale).charAt(1)
                }
            })

            if (DEBUGMODE) {
                console.log("Final formed JSON", chartJson);
            }
            console.log("Drawing HighCharts");
            Highcharts.chart('container', chartJson);

            break;
        }
        case "HighMaps": {
            loadJS("//code.highcharts.com/mapdata/custom/world-robinson-highres.js",
                () => {

                    mapJson = originalDataJSONobj.mapDescription;

                    if (mapJson.zoomTo != null) {
                        // Add id to the [iso-a2, value] keys
                        mapJson.series[0].keys.push('id');

                        // Append to the received data the iso-a2 value as id.
                        // mapData is now [iso-a2, value, id]
                        responseData.dataTable.forEach(mapData => {
                            mapData.push(mapData[0]);
                        });
                    }
                    // Assign the queried data to the highmaps ready Json
                    mapJson.series[0].data = responseData.dataTable;

                    if (DEBUGMODE)
                        console.log("Drawing HighMaps", mapJson);

                    var mapChart = Highcharts.mapChart('container', mapJson);

                    if (mapJson.zoomTo != null && mapJson.zoomTo.destination != null) {
                        mapChart.get(mapJson.zoomTo.destination).zoomTo();
                        mapChart.mapZoom(mapJson.zoomTo.zoomValue);
                    }
                });
            Highcharts.setOptions({
                lang: {
                    decimalPoint: (1.1).toLocaleString(userLocale).charAt(1),
                    thousandsSep: (1000).toLocaleString(userLocale).charAt(1)
                }
            })

            break;
        }
        case "GoogleCharts": {
            var data = fillGoogleChartsDataTable(responseData, originalDataJSONobj);
            if (DEBUGMODE) {
                console.log("Drawing GoogleCharts \nOptions | ChartType | Data",
                    originalDataJSONobj.chartDescription.options, originalDataJSONobj.chartDescription.chartType, data);
            }

            const chartType = getCompatibleGoogleChartsType(originalDataJSONobj.chartDescription.chartType);

            var wrapper = new google.visualization.ChartWrapper({
                chartType: chartType,
                dataTable: data,
                options: originalDataJSONobj.chartDescription.options,
                containerId: 'container'
            });

            if (originalDataJSONobj.chartDescription.options.exporting) {

                google.visualization.events.addListener(wrapper, 'ready', function () {

                    // Create a DOM element that saves the chart
                    var buttonElement = document.createElement("button");
                    buttonElement.innerHTML = "Download as PNG";
                    buttonElement.style = "margin: auto;"
                    buttonElement.onclick = () => {
                        const element = document.createElement('a');
                        element.setAttribute('href', wrapper.getChart().getImageURI());
                        element.setAttribute('download', 'chart.png');
                        element.style.display = 'none';
                        document.body.appendChild(element);
                        element.click();
                        document.body.removeChild(element);
                    }

                    const container = document.getElementById("container");
                    container.parentNode.insertBefore(buttonElement, container.nextSibling);
                });
            }

            wrapper.draw();
            break;
        }
        case "eCharts": {
            var myChart = echarts.init(document.getElementById('container'));
            myChart.showLoading();

            var chartJson = convertToValideChartsJson(responseData, originalDataJSONobj, ChartDataFormatterReadyJSONobj);

            if (DEBUGMODE) {
                console.log("chartJson", chartJson);
                console.log("Drawing eCharts");
            }

            myChart.hideLoading();
            myChart.setOption(chartJson);

            break;
        }
        default:
            if (DEBUGMODE)
                console.error("How did it end up here: " + libraryType);

    }

}

function fillGoogleChartsDataTable(responseData, originJson) {

    var data = new google.visualization.DataTable();
    var dataColumns = responseData.columns;
    var columnsType = responseData.columnsType;
    const chartType = originJson.chartDescription.chartType;

    // datacolumns has the same size of columnsType PLUS a header column
    if (dataColumns.length > 0 && (columnsType === null || ((dataColumns.length === (columnsType.length + 1))))) {

        if (columnsType !== null)
            originJson.chartDescription.options.series = new Array(columnsType.length);

        for (let index = 0; index < dataColumns.length; index++) {
            let value = formatIfNumeric(dataColumns[index]) // format values for Google Charts
            if (index == 0) {
                // Column for the name of the Data entry
                data.addColumn('string', value);

                // Added a column that represents the ID of the parent node in a TreeMap.
                if (chartType === 'TreeMap')
                    data.addColumn('string', 'Parent')
            } else {
                // Column for the value of the Data entry
                data.addColumn('number', value);
                if (columnsType !== null)
                    originJson.chartDescription.options.series[index - 1] = {type: columnsType[index - 1]};
            }
        }
        // Added a row for a Root element as a GoogleChart TreeMaps does not have more than one root nodes
        if (chartType === 'TreeMap') {
            rootNode = ['Root', null];
            for (let index = 1; index < dataColumns.length; index++)
                rootNode.push(null);

            responseData.dataTable.forEach(row => {
                row.splice(1, 0, 'Root')
            });
            responseData.dataTable.splice(0, 0, rootNode);
        }
        data.addRows(responseData.dataTable);
    }

    return data;
}

function convertToValidHighchartJson(responseData, originJson) {

    var convertedJson = originJson.chartDescription;
    var seriesLength = Object.keys(responseData.series).length;

    if (convertedJson.series == null || convertedJson.series.length !== seriesLength)
        convertedJson.series = new Array(seriesLength);

    for (let index = 0; index < seriesLength; index++) {
        var seriesInstance = new Object();

        // Propagate if this data series will be stacking
        if (convertedJson.series[index] != null && convertedJson.series[index].stacking != null)
            seriesInstance.stacking = convertedJson.series[index].stacking;

        // Pass the data series name to the response data object
        //TODO check that this one did not break anything else!!!!!
        // if(responseData.dataSeriesNames != null)
        if (responseData.dataSeriesNames !== undefined && responseData.dataSeriesNames !== null)
            seriesInstance.name = responseData.dataSeriesNames[index];

        // Pass the data series type to the response data object
        if (responseData.dataSeriesTypes !== null)
            seriesInstance.type = responseData.dataSeriesTypes[index];

        // Pass the data series color to the response data object
        if (seriesLength === Object.keys(originJson.chartDescription.queries).length) {
            if (originJson.chartDescription.queries[index].color)
                seriesInstance.color = originJson.chartDescription.queries[index].color;
        }

        if (seriesInstance.type === "treemap") {
            // Add the squarified layout algorithm as a default in treemaps
            seriesInstance.layoutAlgorithm = 'squarified';
            seriesInstance.data = [];
            for (let dataIndex = 0; dataIndex < responseData.series[index].data.length; dataIndex++) {

                var dataValue = responseData.series[index].data[dataIndex]
                var dataName = responseData.xAxis_categories[dataIndex];

                seriesInstance.data.push({name: dataName, value: dataValue, colorValue: dataValue});
            }
        } else if (seriesInstance.type === "dependencywheel" || seriesInstance.type === "sankey") {
            seriesInstance.data = responseData.series[index].data;
            seriesInstance.keys = responseData.series[index].keys;
        } else {
            seriesInstance.data = responseData.series[index].data;

            if (convertedJson.xAxis === undefined)
                convertedJson.xAxis = {};
            convertedJson.xAxis.categories = responseData.xAxis_categories;

            //TODO check that we do not need to use the default 'linear' type of xAxis
            if (responseData.xAxis_categories === undefined && (seriesInstance.type === "column" || seriesInstance.type === "bar"))
                convertedJson.xAxis.type = "category";
        }

        convertedJson.series[index] = seriesInstance;
    }

    if (responseData.drilldown !== undefined && responseData.drilldown !== null) {
        convertedJson.drilldown = new Object();
        convertedJson.drilldown.series = new Array(Object.keys(responseData.drilldown).length);

        for (let index = 0; index < Object.keys(responseData.drilldown).length; index++) {

            convertedJson.drilldown.series[index] = new Object();
            convertedJson.drilldown.series[index].data = responseData.drilldown[index].data;
            // TODO: check if needed for drilldown
            // convertedJson.drilldown.series[index].data = formatIfNumeric(responseData.drilldown[index].data);

            convertedJson.drilldown.series[index].id = responseData.series[0].data[index].drilldown;
            convertedJson.drilldown.series[index].name = responseData.series[0].data[index].drilldown;
            // TODO: check if needed for drilldown
            // convertedJson.drilldown.series[index].name = formatIfNumeric(responseData.series[0].data[index].drilldown);

            // ! Hardcoded Selection that a drilldown is always a pie !
            // As of now drilldown is ONLY used in a pie-graph of a single series with a second group by
            // convertedJson.drilldown.series[index].type = "pie";
            convertedJson.drilldown.series[index].type = seriesInstance.type;
        }
    }

    return convertedJson;
}

function convertToValideChartsJson(responseData, originJson, ChartDataFormatterReadyJSONobj) {

    console.log("ChartDataFormatterReadyJSONobj", ChartDataFormatterReadyJSONobj);
    console.log("OriginJson.chartDescription", originJson.chartDescription);

    var convertedJson = originJson.chartDescription;
    var seriesLength = Object.keys(responseData.series).length;

    if (convertedJson.series == null)
        convertedJson.series = new Array(seriesLength);

    for (let index = 0; index < seriesLength; index++) {
        if (convertedJson.series[index] == null)
            convertedJson.series[index] = new Object();

        var seriesInstance = convertedJson.series[index];

        if (responseData.dataSeriesNames !== null)
            seriesInstance.name = responseData.dataSeriesNames[index];

        // Propagate if this data series will be stacking
        //fixme stack is only passed on the first series of the originJson so we get it from there
        if (convertedJson.series[index] != null && convertedJson.series[0].stack != null)
            seriesInstance.stack = convertedJson.series[0].stack;

        // Pass the data series type to the response data object
        if (responseData.dataSeriesTypes !== null)
            seriesInstance.type = responseData.dataSeriesTypes[index];

        // // Pass the data series color to the response data object
        // if(seriesLength === Object.keys(originJson.chartDescription.queries).length) {
        //     if (originJson.chartDescription.queries[index].color)
        //         seriesInstance.color = originJson.chartDescription.queries[index].color;
        // }

        // Series Data alignment
        if (seriesInstance.type === "treemap") {
            seriesInstance.data = [];
            for (let dataIndex = 0; dataIndex < responseData.series[index].data.length; dataIndex++) {

                var dataValue = responseData.series[index].data[dataIndex]
                var dataName = responseData.xAxis_categories[dataIndex];

                seriesInstance.data.push({name: dataName, value: dataValue});
            }
        } else
            seriesInstance.data = responseData.series[index].data;

        // Series type formatting

        switch (seriesInstance.type) {
            case "dependencywheel":
                // Connecting the graph links
                seriesInstance.links = responseData.series[index].links;

                // Dependency wheel is a circular graph in eCharts
                seriesInstance.type = "graph";
                seriesInstance.layout = "circular";
                seriesInstance.circular = {rotateLabel: true};
                seriesInstance.roam = true;
                seriesInstance.label = {position: 'right', formatter: '{b}'};
                seriesInstance.lineStyle = {curveness: 0.3};

                // Making the label show on a data node with symbolSize > 30
                responseData.series[index].data.forEach(function (node) {
                    node.symbolSize = node.value > 150 ? 150 : node.value * (2 / 3);
                    node.label = {show: node.symbolSize > 30};
                });

                // Don't show axes
                convertedJson.xAxis.show = false;
                convertedJson.yAxis.show = false;

                // Change color
                convertedJson.colorBy = "data";

                break;
            case "sankey":
                // Connecting the graph links
                seriesInstance.links = responseData.series[index].links;
                seriesInstance.layout = "none";
                seriesInstance.emphasis = {focus: "adjacency"};

                // Don't show axes
                convertedJson.xAxis.show = false;
                convertedJson.yAxis.show = false;

                // Change color
                convertedJson.colorBy = "data";

                break;
            case "bar":
                // in eCharts a bar chart is a bar chart with the categories on yAxis
                convertedJson.yAxis = {data: responseData.xAxis_categories};
                break;
            case "area":
                seriesInstance.type = "line";
                seriesInstance.areaStyle = {};
                convertedJson.xAxis = {data: responseData.xAxis_categories};
                break;
            case "pie":
            case "treemap":
                convertedJson.xAxis = null;
                convertedJson.yAxis = null;
                break;
            default:
                convertedJson.xAxis = {data: responseData.xAxis_categories}
                break;
        }

        // // in eCharts a bar chart is a bar chart with the categories on yAxis
        // if(seriesInstance.type === 'bar')
        //     convertedJson.yAxis = {data: responseData.xAxis_categories};
        // else if(convertedJson.series[0].type === 'pie' || convertedJson.series[0].type === 'treemap')
        // {
        //     convertedJson.xAxis = null;
        //     convertedJson.yAxis = null;
        // }
        // else
        //     convertedJson.xAxis = {data: responseData.xAxis_categories};

        // in eCharts a column chart is a bar chart
        if (seriesInstance.type === 'column')
            seriesInstance.type = 'bar';

        if (Object.keys(responseData.series).length === Object.keys(originJson.chartDescription.queries).length && !(seriesInstance.type === "graph" || seriesInstance.type === "sankey"))
            //TODO if (originJson.chartDescription.queries[index].color)
            seriesInstance.color = originJson.chartDescription.queries[index].color;

        convertedJson.series[index] = seriesInstance;
    }

    console.log("convertedJson", convertedJson);

    return convertedJson;
}
