import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:frontend/component/menu/appliance_management.dart';
import 'package:frontend/component/menu/help_screen.dart';
import 'package:frontend/component/menu/room_management.dart';
import 'package:frontend/const/global_alert_dialog.dart';
import 'package:frontend/controller/map_data_controller.dart';
import 'package:frontend/controller/menu_tab_controller.dart';
import 'package:frontend/const/colors.dart';
import 'package:frontend/service/room_service.dart';
import 'package:frontend/socket/socket.dart';
import 'package:get/get.dart';
import 'package:logger/logger.dart';
import 'package:stomp_dart_client/stomp_frame.dart';
import 'login_screen.dart';

Logger logger = Logger();

class MenuScreen extends StatefulWidget {
  const MenuScreen({super.key});

  @override
  State<MenuScreen> createState() => _MenuScreenState();
}

class _MenuScreenState extends State<MenuScreen> {
  MenuTabController menuTabController = MenuTabController();
  final FlutterSecureStorage secureStorage = const FlutterSecureStorage();
  final SocketController socketController = Get.find<SocketController>();
  MapDataController mapDataController = MapDataController();

  void _getHomeScanResponse(StompFrame frame) {
    final data = json.decode(frame.body ?? '{}');
    if (data['type'] == 'COMPLETE') {
      logger.d("HOME SCAN COMPLETE");
      mapDataController.fetchMapData();
    }
  }

  @override
  void initState() {
    super.initState();
    // 콜백 함수를 포함하여 MenuTabController 초기화
    menuTabController = MenuTabController(
      onHomeScan: () async {
        final String? homeId = await secureStorage.read(key: "homeId");
        logger.e("$homeId");
        socketController.subscribeToDestination(
          "/exchange/control.exchange/home.$homeId",
          _getHomeScanResponse,
        );
        socketController.sendMessage(
          destination: '/pub/control.message.$homeId',
          type: 'SCAN',
          messageContent: '',
        );
        GlobalAlertDialog.show(
          context,
          title: "알림",
          message: "홈스캔을 시작합니다.\n홈스캔이 완료되면 알림이 제공됩니다.",
        );
      },
      onManageRoom: () {
        Navigator.of(context).push(MaterialPageRoute(
          builder: (context) => const RoomManagement(), // '방 관리' 페이지로 이동
        ));
      },
      onManageAppliance: () => _handleApplianceManagementTap(),
      onHelp: () {
        Navigator.of(context).push(MaterialPageRoute(
          builder: (context) => const HelpScreen(), // '방 관리' 페이지로 이동
        ));
      },
      onLogout: () async {
        await secureStorage.delete(key: "isLoggedIn");

        Navigator.of(context).pushReplacement(MaterialPageRoute(
          builder: (context) => const LoginScreen(), // 로그아웃 후 로그인 화면으로 전환
        ));
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        color: white, // 전체 배경색 설정
        padding: const EdgeInsets.only(top: 12), // 상단 padding 추가
        child: Container(
          padding: const EdgeInsets.all(15), // 전체적인 마진 설정
          decoration: BoxDecoration(
            color: Colors.white, // 배경색 설정
            boxShadow: [
              BoxShadow(
                color: Colors.grey.withOpacity(0.5),
                spreadRadius: 3,
                blurRadius: 4,
                offset: const Offset(0, 3), // 그림자 위치 조정
              ),
            ],
            borderRadius: const BorderRadius.only(
              topLeft: Radius.circular(20.0),
              topRight: Radius.circular(20.0),
            ), // 모서리 둥글게
          ),
          child: Column(
            children: [
              Container(
                decoration: BoxDecoration(
                  color: Colors.grey[200],
                  borderRadius: BorderRadius.circular(10),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.grey.withOpacity(0.5),
                      spreadRadius: 1,
                      blurRadius: 1,
                      offset: const Offset(0, 1), // 그림자 위치 조정
                    ),
                  ],
                ),
                child: Column(
                  children: [
                    _buildMenuItem(Icons.android, "기기 등록"),
                    _buildDivider(),
                    _buildMenuItem(Icons.home_outlined, "홈 스캔"),
                    _buildDivider(),
                    _buildMenuItem(Icons.meeting_room_outlined, "방 관리"),
                    _buildDivider(),
                    _buildMenuItem(Icons.cloud_download_outlined, "가전 관리"),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              Container(
                decoration: BoxDecoration(
                  color: Colors.grey[200],
                  borderRadius: BorderRadius.circular(10),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.grey.withOpacity(0.5),
                      spreadRadius: 1,
                      blurRadius: 1,
                      offset: const Offset(0, 1), // 그림자 위치 조정
                    ),
                  ],
                ),
                child: Column(
                  children: [
                    _buildMenuItem(Icons.help_outline_outlined, "도움말"),
                    _buildDivider(),
                    _buildMenuItem(Icons.exit_to_app, "로그아웃"),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMenuItem(IconData icon, String title) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      onTap: () => menuTabController.tabItem(title),
    );
  }

  void _handleApplianceManagementTap() async {
    RoomService roomService = RoomService();
    // '가전 관리' 탭 로직 구현
    final rooms = await roomService.getRooms();
    if (rooms.isEmpty) {
      // 방 목록이 비어있을 경우 알림 창을 띄움
      GlobalAlertDialog.show(
        context,
        title: "알림",
        message: "방을 먼저 등록해주세요.",
      );
    } else {
      // 방 목록이 있을 경우 '가전 관리' 페이지로 이동
      Navigator.of(context).push(MaterialPageRoute(
        builder: (context) => const ApplianceManagement(),
      ));
    }
  }

  Widget _buildDivider() => const Divider(height: 1, thickness: 1);

  @override
  void dispose() {
    super.dispose();
  }
}
