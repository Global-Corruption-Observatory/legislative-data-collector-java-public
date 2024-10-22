package com.precognox.ceu.legislative_data_collector.chile.utils;

public class ChileApiUrls {
    public final static String LAW_LIST_API = "https://nuevo.leychile.cl/servicios/Consulta/listaresultadosavanzada?stringBusqueda=%s&npagina=%d&itemsporpagina=%d";
    public final static String LAW_PAGE_API = "https://nuevo.leychile.cl/servicios/Navegar/get_norma_json?idNorma=%s";
    public final static String LAW_TERMINATION_DATE_API = "https://nuevo.leychile.cl/servicios/Consulta/getRefundidas?idNorma=%s";
    public final static String LAW_ORIGINATOR_API = "https://nuevo.leychile.cl/servicios/Navegar/get_autores_de_la_ley?idNorma=%s";
    public final static String ORIGINATOR_PAGE_API = "https://www.bcn.cl/laborparlamentaria/wsgi/consulta/verLaborParlamentaria.py?idPersona=%s";
    public final static String BILL_LIST_API = "https://www.senado.cl/appsenado/index.php?mo=tramitacion&ac=boletin_x_fecha&que_fecha=1&desde=%s&hasta=%s";
    public final static String BILL_PAGE_API = "https://www.senado.cl/appsenado/index.php?mo=tramitacion&ac=datos_proy&nboletin=%s";
    public final static String LEGISLATIVE_STAGES_API = "https://www.senado.cl/appsenado/index.php?mo=tramitacion&ac=tramites&proyid=%s";
    public final static String COMMITTEES_API = "https://www.senado.cl/appsenado/index.php?mo=tramitacion&ac=informes&proyid=%s";
    public final static String BILL_ORIGINATOR_API = "https://www.senado.cl/appsenado/index.php?mo=tramitacion&ac=autores&proyid=%s";
    public final static String PROCEDURE_TYPE_API = "https://www.senado.cl/appsenado/index.php?mo=tramitacion&ac=urgencias&proyid=%s";
    public final static String BILL_TEXT_API = "https://www.senado.cl%s";
    public final static String MODIFIED_LAWS_URL = "https://nuevo.leychile.cl/servicios/Vinculaciones/get_vinculaciones?npagina=1&itemsporpagina=%d&clase_vinculacion=modificacion&sentidoVinculacion=modifica_a&tipoAgrupacion=agrupar&fechaVigencia=1000-01-01&idNorma=%s";
    public final static String AFFECTING_LAWS_URL = "https://nuevo.leychile.cl/servicios/Vinculaciones/get_vinculaciones?npagina=1&itemsporpagina=%d&clase_vinculacion=modificacion&sentidoVinculacion=modifica_por&tipoAgrupacion=agrupar&fechaVigencia=1000-01-01&idNorma=%s";
    public final static String AFFECTING_LAWS_DETAILED_URL = "https://nuevo.leychile.cl/servicios/Vinculaciones/get_vinculaciones?npagina=1&itemsporpagina=%d&clase_vinculacion=modificacion&sentidoVinculacion=modifica_por&fechaVigencia=1000-01-01&idNorma=%s";
}
