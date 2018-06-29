//This variable holds the Library state across the execution of the Chart Formation
var libraryType;
var port = "8080";
var domainName = "localhost";
var protocol = "http";

var domainLink = protocol+"://"+domainName+":"+port;

function fetchChart(jsonData){
    $.getJSON(jsonData, handleAdminSideData)
    .done()
    .fail(()=>{console.log("Failed");})
    .always();
}

//Function for loading(= appending to the head) a JS file
function loadJS(url, afterLoadCallback){
    
    var fileref=document.createElement('script');
    fileref.onload = afterLoadCallback;
    fileref.setAttribute("type","text/javascript");
    fileref.setAttribute("src", url);
    var firstHeadTaginDOM = document.getElementsByTagName('head')[0];
    firstHeadTaginDOM.appendChild(fileref);
        
    console.log("Library "+firstHeadTaginDOM.lastChild.getAttribute("src")+" loaded!");
}

//Callback that handles the data sent from the Admin part of the app
function handleAdminSideData(dataJSONobj)
{   
    // dataJSONobj holds the option-ready version of the JSON that will be passed to the Chart library,
    // along with the queries that must be passed to ChartDataFormatter and eventually to DBAccess
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
                dataJSONobj.chartDescription.queries.
                forEach(element => {
                    var ChartInfoObj = new Object();
                    ChartInfoObj.query = element;
                    RequestInfoObj.chartsInfo.push(ChartInfoObj);
                });

                passToChartDataFormatter(dataJSONobj,RequestInfoObj,
                    domainLink+"/chart");
            });            
        });
        break;
    }
    case "HighCharts":
    {
                
        //Dynamically add JS library
        loadJS("https://code.highcharts.com/6.0/highcharts.js",
        
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
            dataJSONobj.chartDescription.series.
            forEach(element => {
                var ChartInfoObj = new Object();
    
                if(element.type === undefined)
                    ChartInfoObj.type = defaultType;
                else
                    ChartInfoObj.type = element.type;
                
                ChartInfoObj.query = element.query;
                RequestInfoObj.chartsInfo.push(ChartInfoObj);
            });

            passToChartDataFormatter(dataJSONobj,RequestInfoObj,
                        domainLink+"/chart"); }
        );
        break;
    }
    default:
        console.log("Unsupported Library: "+ dataJSONobj.library);
    }
}

//Post the admin-side json to the ChartDataFormatter
function passToChartDataFormatter(dataJSONobj,ChartDataFormatterReadyJSONobj,ChartDataFormatterUrl)
{
    console.log("Passing to CDF:");
    console.log(ChartDataFormatterReadyJSONobj);

    $.ajax(
    {url: this.ChartDataFormatterUrl,
    type: "POST",
    dataType: "json",
    contentType: 'application/json; charset=utf-8',
    data: JSON.stringify(ChartDataFormatterReadyJSONobj),
    cache: false,
    success: function(data){ handleChartDataFormatterResponse(data,dataJSONobj) }            
    })
    .done()
    .fail()
    .always();
}

function handleChartDataFormatterResponse(responseData, originalDataJSONobj)
{   
    console.log("Got from CDF:");
    console.log(responseData);
    
    //Hide children elements of container
    $("#container").children().remove();
    
    if(libraryType === "GoogleCharts"){

        var data = fillGoogleChartsDataTable(responseData, originalDataJSONobj);
       
        var wrapper = new google.visualization.ChartWrapper({
            chartType: originalDataJSONobj.chartDescription.chartType,
            dataTable: data,
            options: originalDataJSONobj.chartDescription.options,
            containerId: 'container'
            });

        wrapper.draw();
    }
    if(libraryType === "HighCharts"){
        var chartJson = convertToValidHighchartJson(responseData, originalDataJSONobj);
        console.log(chartJson);
        console.log("Drawing HighCharts");
        Highcharts.chart('container',chartJson);
    }

}

function fillGoogleChartsDataTable(responseData, originJson){

    var data = new google.visualization.DataTable();
    var dataColumns = originJson.chartDescription.columns;

    for(let index = 0; index < dataColumns.length; index++){
        if(index == 0)
            data.addColumn('string', dataColumns[index]);
        else
            data.addColumn('number', dataColumns[index]);
    }
    data.addRows(responseData.dataTable);

    return data;
}

function convertToValidHighchartJson(responseData, originJson){

    var convertedJson = originJson.chartDescription;
    if(Object.keys(convertedJson.series).length != Object.keys(responseData.series).length)
        return null;
    
    for (let index = 0; index < Object.keys(convertedJson.series).length; index++){
        convertedJson.series[index].data = responseData.series[index].data;
        convertedJson.series[index].query = null;
    } 
    if(convertedJson.xAxis === undefined)
        convertedJson.xAxis = {};    
    convertedJson.xAxis.categories = responseData.xAxis_categories;

    return convertedJson;
}