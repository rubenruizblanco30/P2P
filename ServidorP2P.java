package p2p;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class ServidorP2P {

    static HashMap<String, Usuario> usuarios = new HashMap<>();
    static Logger registro = Logger.getLogger(ServidorP2P.class.getName());

    static class Usuario {
        String ip;
        int puerto;
        List<String> archivos;

        Usuario(String ip, int puerto, List<String> archivos) {
            this.ip = ip;
            this.puerto = puerto;
            this.archivos = archivos;
        }
    }

    public static void main(String[] args) {
        try (ServerSocket servidor = new ServerSocket(5000)) {
            registro.info("Servidor iniciado en el puerto 5000");

            while (true) {
                Socket cliente = servidor.accept();

                Thread hilo = new Thread(() -> atender(cliente));
                hilo.start();
            }

        } catch (IOException e) {
            registro.warning("Error del servidor: " + e.getMessage());
        }
    }

    static void atender(Socket cliente) {
        String nombre = null;

        try (Socket conexion = cliente;
             BufferedReader entrada = new BufferedReader(
                 new InputStreamReader(
                     conexion.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter salida = new PrintWriter(
                 new OutputStreamWriter(
                     conexion.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String ip = conexion.getInetAddress().getHostAddress();
            String mensaje;

            while ((mensaje = entrada.readLine()) != null) {
                mensaje = mensaje.trim();
                String[] datos = mensaje.split("\\s+", 4);

                switch (datos[0]) {

                    case "REGISTER":
                        if (nombre != null) {
                            salida.println("ERROR Ya estas registrado");
                            break;
                        }

                        String respuesta = registrar(datos, ip);

                        if (respuesta.equals("OK")) {
                            nombre = datos[1];
                        }

                        salida.println(respuesta);
                        break;

                    case "LIST":
                        salida.println("OK " + listar());
                        break;

                    case "SEARCH":
                        String archivo = mensaje.substring(6).trim();

                        if (archivo.isEmpty()) {
                            salida.println("ERROR Uso: SEARCH archivo");
                        } else {
                            salida.println(buscar(archivo));
                        }
                        break;

                    case "UNREGISTER":
                        if (nombre == null) {
                            salida.println("ERROR No estas registrado");
                        } else {
                            eliminar(nombre);
                            nombre = null;
                            salida.println("OK");
                        }
                        break;

                    default:
                        salida.println("ERROR Comando desconocido");
                }
            }

        } catch (IOException e) {
            registro.warning("Error con el cliente: " + e.getMessage());

        } finally {
            if (nombre != null) {
                eliminar(nombre);
            }
        }
    }

    static synchronized String registrar(String[] datos, String ip) {
        if (datos.length < 3) {
            return "ERROR Uso: REGISTER nombre puertoUDP [archivo1,archivo2]";
        }

        String nombre = datos[1];
        int puerto;

        try {
            puerto = Integer.parseInt(datos[2]);
        } catch (NumberFormatException e) {
            return "ERROR Puerto UDP invalido";
        }

        if (puerto < 1 || puerto > 65535) {
            return "ERROR Puerto UDP invalido";
        }

        if (usuarios.containsKey(nombre)) {
            return "ERROR Nombre en uso";
        }

        List<String> archivos = new ArrayList<>();

        if (datos.length == 4) {
            archivos = Arrays.asList(datos[3].split("\\s*,\\s*"));
        }

        usuarios.put(nombre, new Usuario(ip, puerto, archivos));

        registro.info("Alta de " + nombre + ". Usuarios: " + listar());
        return "OK";
    }

    static synchronized String listar() {
        return String.join(",", usuarios.keySet());
    }

    static synchronized String buscar(String archivo) {
        for (Usuario usuario : usuarios.values()) {
            if (usuario.archivos.contains(archivo)) {
                return "FOUND " + usuario.ip + " " + usuario.puerto;
            }
        }

        return "NOT_FOUND";
    }

    static synchronized void eliminar(String nombre) {
        usuarios.remove(nombre);
        registro.info("Baja de " + nombre + ". Usuarios: " + listar());
    }
}