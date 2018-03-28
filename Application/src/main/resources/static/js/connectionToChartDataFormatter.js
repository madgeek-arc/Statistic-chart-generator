//This variable holds the Library state across the execution of the Chart Formation
var libraryType;
var port = "8080";
var domainName = "localhost";
var protocol = "http";

var domainLink = protocol+"://"+domainName+":"+port;

function fetchChart(jsonData){
    $.getJSON(jsonData, handleAdminSideData)
    .done()
    .fail()
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

    //Dynamically add JS library
    if(dataJSONobj.library === "Highcharts"){
        
        var RequestInfoObj = new Object();
        //Pass the Chart library to ChartDataFormatter
        RequestInfoObj.library = dataJSONobj.library;
        
        //Hold the Library state
        libraryType = RequestInfoObj.library;

        //Pass the Chart type to ChartDataFormatter
        RequestInfoObj.type = dataJSONobj.chartDescription.chart.type;
        //Create ChartInfo Object Array
        RequestInfoObj.chartsInfo = [];

        //Creat ChartInfo and pass the Chart data queries to ChartDataFormatter
        //along with the requested Chart type
        dataJSONobj.chartDescription.series.
        forEach(element => {
            var ChartInfoObj = new Object();

            if(element.type === undefined)
                ChartInfoObj.type = RequestInfoObj.type;
            else
                ChartInfoObj.type = element.type;
            
            ChartInfoObj.query = element.query;
            RequestInfoObj.chartsInfo.push(ChartInfoObj);
        });

        loadJS("https://code.highcharts.com/6.0/highcharts.js",
        function(){ passToChartDataFormatter(dataJSONobj,
                        RequestInfoObj,
                        domainLink+"/chart"); });
    }
}

//Post the admin-side json to the ChartDataFormatter
function passToChartDataFormatter(dataJSONobj,ChartDataFormatterReadyJSONobj,ChartDataFormatterUrl)
{
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
    console.log(responseData);
    
    //Hide children elements of container
    $("#container").children().remove();
    
    if(libraryType === "Highcharts"){
        var chartJson = convertToValidHighchartJson(responseData, originalDataJSONobj);
        console.log(chartJson);
        Highcharts.chart('container',chartJson);
    }

}

function convertToValidHighchartJson(responseData, originJson){

    var convertedJson = originJson.chartDescription;
    if(Object.keys(convertedJson.series).length != Object.keys(responseData.series).length)
        return null;
    
    for (let index = 0; index < Object.keys(convertedJson.series).length; index++){
        convertedJson.series[index].data = responseData.series[index].data;
        convertedJson.series[index].query = null;
        
        if(convertedJson.xAxis !== undefined)
            convertedJson.xAxis.categories = responseData.xAxis_categories
        else
            convertedJson.xAxis = {};
            convertedJson.xAxis.categories = responseData.xAxis_categories;
    } 

    return convertedJson;
}