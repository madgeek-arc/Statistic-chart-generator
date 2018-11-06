//This variable holds the Library state across the execution of the Chart Formation
var libraryType;
var port = "8080";
var domainName = "localhost";
var protocol = "http";

var domainLink = protocol+"://"+domainName+":"+port;
const DEBUGMODE = false;

function fetchChart(jsonData){
    $.getJSON(jsonData, handleAdminSideData)
    .done()
    .fail(()=>{console.log("Failed");})
    .always();
}

//Function for loading(= appending to the head) a JS file
function loadJS(url, afterLoadCallback){
    
    var fileref=document.createElement('script');
    var firstHeadTaginDOM = document.getElementsByTagName('head')[0];
    var callback = ()=>{
        if(DEBUGMODE)
            console.log("Library "+document.getElementsByTagName('head')[0].lastChild.getAttribute("src")+" about to be loaded!");
        afterLoadCallback();
    };

    fileref.onload = callback;
    fileref.onreadystatechange = callback;
    fileref.setAttribute("type","text/javascript");
    fileref.setAttribute("src", url);
    
    firstHeadTaginDOM.appendChild(fileref);        
}

//Callback that handles the data sent from the Admin part of the app
function handleAdminSideData(dataJSONobj)
{   
    // dataJSONobj holds the option-ready version of the JSON that will be passed to the Chart library,
    // along with the queries that must be passed to ChartDataFormatter and eventually to DBAccess
    if(DEBUGMODE)
        console.log(dataJSONobj);

    switch(dataJSONobj.library){
    case "GoogleCharts":
    {
        
        loadJS("https://www.gstatic.com/charts/loader.js",                 
        function(){            
            google.charts.load('45', {packages: ['corechart']});
            google.charts.setOnLoadCallback(function(){
                libraryType = dataJSONobj.library;
                
                var RequestInfoObj = new Object();
                //Pass the Chart library to ChartDataFormatter
                RequestInfoObj.library = dataJSONobj.library;
                
                //Create ChartInfo Object Array
                RequestInfoObj.chartsInfo = [];
                //Create ChartInfo and pass the Chart data queries to ChartDataFormatter
                //along with the requested Chart type
                RequestInfoObj.chartsInfo = dataJSONobj.chartDescription.queriesInfo;

                passToChartDataFormatter(dataJSONobj,RequestInfoObj,
                            domainLink+"/chart");
            });            
        });
        break;
    }
    case "HighCharts":
    {
                
        //Dynamically add JS library
        loadJS("https://code.highcharts.com/highcharts.js",        
        () => loadJS("https://code.highcharts.com/modules/drilldown.js",
        () => loadJS("https://code.highcharts.com/modules/no-data-to-display.js",
        () => loadJS("https://code.highcharts.com/highcharts-3d.js",
        () => loadJS("https://code.highcharts.com/modules/exporting.js",
        
        function(){ 
            //Hold the Library state
            libraryType = dataJSONobj.library;

            var RequestInfoObj = new Object();
            //Pass the Chart library to ChartDataFormatter
            RequestInfoObj.library = dataJSONobj.library;        
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
            
        )))));
        
        break;
    }
    default:
        if(DEBUGMODE)
            console.log("Unsupported Library: "+ dataJSONobj.library);
    }
}

//Post the admin-side json to the ChartDataFormatter
function passToChartDataFormatter(dataJSONobj,ChartDataFormatterReadyJSONobj,ChartDataFormatterUrl)
{
    if(DEBUGMODE) {
        console.log("Passing to CDF:");
        console.log(ChartDataFormatterReadyJSONobj);
    }
    
    $.ajax(
    {url: this.ChartDataFormatterUrl,
    type: "POST",
    dataType: "json",
    contentType: 'application/json; charset=utf-8',
    data: JSON.stringify(ChartDataFormatterReadyJSONobj),
    cache: false,
    success: function(data){ handleChartDataFormatterResponse(data,dataJSONobj) },
    error: function( jqXHR, textStatus, errorThrown) { 

        $('#loadingImg').hide();
        if (jqXHR.status === 0) {
            $('#errorSpan').show().html('Not connected.\nPlease verify your network connection.');
        } else if (jqXHR.status == 404) {
            $('#errorSpan').show().html('The requested page not found [404]');
        } else if (jqXHR.status == 500) {
            $('#errorSpan').show().html('Internal Server Error [500]');
        } else if (jqXHR.status == 422) {
            $('#errorSpan').show().html('Chart generation went wrong [422]');
        } else if (jqXHR.status == 504) {
            $('#errorSpan').show().html('Server took unusually too long to respond [504]');
        }
        else {
            $('#errorSpan').show().html('Something unexpected happened [' + jqXHR.status + ']');
        }

     }
    }).done()
    .fail() 
    .always();
}

function handleChartDataFormatterResponse(responseData, originalDataJSONobj)
{   
    if(DEBUGMODE) {
        console.log("Got from CDF:");
        console.log(responseData);
    }
    
    //Hide children elements of container
    $("#container").children().remove();
    
    if(libraryType === "GoogleCharts"){

        var data = fillGoogleChartsDataTable(responseData, originalDataJSONobj);
        if(DEBUGMODE) {
            console.log('Options:');
            console.log(originalDataJSONobj.chartDescription.options);
        }

        var wrapper;
        if( !originalDataJSONobj.chartDescription.tableForm ) {
            
            wrapper = new google.visualization.ChartWrapper({
                chartType: originalDataJSONobj.chartDescription.chartType,
                dataTable: data,
                options: originalDataJSONobj.chartDescription.options,
                containerId: 'container'
            });

            if(originalDataJSONobj.chartDescription.options.exporting) {

                google.visualization.events.addListener(wrapper, 'ready', function () {

                    // Create a DOM element that saves the chart
                    var buttonElement = document.createElement("button");
                    buttonElement.innerHTML = "Download as PNG";
                    buttonElement.onclick = () => {
                        const element = document.createElement('a');
                        element.setAttribute('href', wrapper.getChart().getImageURI() );
                        element.setAttribute('download', 'chart.png');
                        element.style.display = 'none';
                        document.body.appendChild(element);
                        element.click();
                        document.body.removeChild(element);
                    }
                    $("#container").before(buttonElement);
                });
            }
        } else {
            wrapper = new google.visualization.ChartWrapper({
            chartType: 'Table',
            dataTable: data,
            options: {width: '100%', height: '100%'},
            containerId: 'container'
            });
        }

        wrapper.draw();
    }
    if(libraryType === "HighCharts"){
        var chartJson = convertToValidHighchartJson(responseData, originalDataJSONobj);

        if(DEBUGMODE) {
            console.log(chartJson);
            console.log("Drawing HighCharts");
        }
        
        Highcharts.setOptions({
            lang: {
                drillUpText: '<< Back'
            }
        })
        Highcharts.chart('container',chartJson);
    }

}

function fillGoogleChartsDataTable(responseData, originJson){

    var data = new google.visualization.DataTable();
    var dataColumns = responseData.columns;
    var columnsType = responseData.columnsType;
    
    // datacolumns has the same size of columnsType PLUS a header column
    if(dataColumns.length > 0 && ( columnsType === null || ((columnsType !== null) && (dataColumns.length === (columnsType.length + 1))))){

        if(columnsType !== null) 
            originJson.chartDescription.options.series = new Array(columnsType.length);

        for(let index = 0; index < dataColumns.length; index++){
            if(index == 0)
                data.addColumn('string', dataColumns[index]);
            else{
                data.addColumn('number', dataColumns[index]);
                if(columnsType !== null)
                    originJson.chartDescription.options.series[index-1] = {type: columnsType[index-1]};
            }
        }
        data.addRows(responseData.dataTable);
    }

    return data;
}

function convertToValidHighchartJson(responseData, originJson){

    var convertedJson = originJson.chartDescription;
    convertedJson.series = new Array( Object.keys(responseData.series).length );

    for (let index = 0; index < Object.keys(responseData.series).length; index++){
        var seriesInstance = new Object();
        seriesInstance.data = responseData.series[index].data;
        
        if(responseData.dataSeriesNames !== null)
            seriesInstance.name = responseData.dataSeriesNames[index];
        
        if(responseData.dataSeriesTypes !== null)
            seriesInstance.type = responseData.dataSeriesTypes[index];
            
        if(Object.keys(responseData.series).length === Object.keys(originJson.chartDescription.queries).length)
            seriesInstance.color = originJson.chartDescription.queries[index].color;

        convertedJson.series[index] = seriesInstance;
    }

    if(responseData.drilldown !== null){
        convertedJson.drilldown = new Object();
        convertedJson.drilldown.series = new Array( Object.keys(responseData.drilldown).length );

        for (let index = 0; index < Object.keys(responseData.drilldown).length; index++){
        
            convertedJson.drilldown.series[index] = new Object();
            convertedJson.drilldown.series[index].data = responseData.drilldown[index].data;
            convertedJson.drilldown.series[index].id = responseData.series[0].data[index].drilldown;
            convertedJson.drilldown.series[index].name = responseData.series[0].data[index].drilldown;
        }
    }

    if(convertedJson.xAxis === undefined)
        convertedJson.xAxis = {};    
    convertedJson.xAxis.categories = responseData.xAxis_categories;

    return convertedJson;
}