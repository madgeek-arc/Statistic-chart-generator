var port = "8080";
var domainName = "localhost";
var protocol = "http";

var domainLink = protocol+"://"+domainName+":"+port;
const DEBUGMODE = true;

function drawTable(dataJSONobj){

    google.charts.load('current', {packages: ['table']});
    google.charts.setOnLoadCallback(function(){
        
    var RequestInfoObj = new Object();
    //Pass the Chart library to ChartDataFormatter
    RequestInfoObj.library = dataJSONobj.library;
    
    //Create ChartInfo Object Array
    RequestInfoObj.chartsInfo = [];
    //Create ChartInfo and pass the Chart data queries to ChartDataFormatter
    //along with the requested Chart type
    RequestInfoObj.chartsInfo = dataJSONobj.chartDescription.queriesInfo;

    passToChartDataFormatter(dataJSONobj,RequestInfoObj,
                domainLink+"/table");
    });
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
        }
        else {
            $('#errorSpan').show().html('An unexpected error has occurred [' + jqXHR.status + '].\nPlease try again or <a href="https://www.openaire.eu/support/helpdesk">contact us</a>, if the problem persists.');
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

    var data = fillGoogleChartsDataTable(responseData, originalDataJSONobj);

    var table = new google.visualization.Table(document.getElementById('container'));
    if(data.getNumberOfRows() > 10) {
        table.draw(data, {   
                page: 'enable',
                showRowNumber: true,
                width: '100%', height: 'auto'});
        return;
    }
    table.draw(data, {
        showRowNumber: true,
        width: '100%', height: 'auto'});
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